import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  ADMIN_ENTRY_CLUSTER_PATH,
  ADMIN_ENTRY_FALLBACK_ROUTE,
  ADMIN_ENTRY_ROUTE_NAME,
  resolveAdminEntryTarget,
} from '../src/views/dashboard/monitor/adminEntry'

assert.equal(ADMIN_ENTRY_ROUTE_NAME, 'ComputeNodeIndex')

assert.deepEqual(
  resolveAdminEntryTarget({ hasRoute: () => true }),
  { name: ADMIN_ENTRY_ROUTE_NAME },
  'The dashboard admin entry should prefer the compute-node page only when that route is registered.',
)

assert.deepEqual(
  resolveAdminEntryTarget({
    hasRoute: (name) => name === 'NodeManage',
    getRoutes: () => [
      {
        path: '/node',
        name: 'NodeManage',
        meta: { title: '\u96c6\u7fa4\u7ba1\u7406' },
        redirect: '/node/index',
      },
    ] as any,
  }),
  ADMIN_ENTRY_FALLBACK_ROUTE,
  'The dashboard admin entry should not target a cluster-management parent shell when the child page is not registered.',
)

assert.deepEqual(
  resolveAdminEntryTarget({
    hasRoute: () => false,
    getRoutes: () => [
      {
        path: '/ops/cluster',
        name: 'BackendClusterRoute',
        meta: { title: '\u96c6\u7fa4\u7ba1\u7406' },
      },
    ] as any,
  }),
  ADMIN_ENTRY_FALLBACK_ROUTE,
  'The dashboard admin entry should not target a back-end menu shell that may not render the local cluster page.',
)

assert.deepEqual(
  resolveAdminEntryTarget({ hasRoute: () => false }),
  ADMIN_ENTRY_FALLBACK_ROUTE,
  'The dashboard admin entry should fall back to the local cluster-management path instead of the home page.',
)

assert.deepEqual(
  ADMIN_ENTRY_FALLBACK_ROUTE,
  { path: ADMIN_ENTRY_CLUSTER_PATH },
  'The fallback should navigate directly to cluster management and never back to the dashboard home page.',
)

const monitorHeader = readFileSync(resolve('src/views/dashboard/monitor/components/Header.vue'), 'utf8')
const adminEntry = readFileSync(resolve('src/views/dashboard/monitor/adminEntry.ts'), 'utf8')
const permissionStore = readFileSync(resolve('src/store/modules/permission.ts'), 'utf8')

assert.doesNotMatch(
  monitorHeader,
  /router\.push\(['"]\/node\/index['"]\)/,
  'The dashboard admin entry must not hard-code /node/index because backend menu mode may not register it.',
)

assert.match(
  monitorHeader,
  /resolveAdminEntryTarget\(router\)/,
  'The dashboard admin entry should resolve a safe target before navigating.',
)

assert.doesNotMatch(
  adminEntry,
  /\/dashboard\/index/,
  'The dashboard admin entry fallback must never target the dashboard home page.',
)

assert.match(
  permissionStore,
  /import\s+node\s+from\s+['"]@\/router\/routes\/modules\/node['"]/,
  'Back-end permission mode should import the local cluster-management route.',
)

assert.match(
  permissionStore,
  /transformRouteToMenu\(\[dashboard,\s+\.\.\.requiredLocalBackRoutes,\s+\.\.\.routeList\]\)/,
  'Back-end permission mode should include the local cluster-management route in the visible menu list.',
)

assert.match(
  permissionStore,
  /routes\s+=\s+\[PAGE_NOT_FOUND_ROUTE,\s+dashboard,\s+\.\.\.routeList,\s+node\]/,
  'Back-end permission mode should always register the local cluster-management page after server routes so the admin entry can render it.',
)
