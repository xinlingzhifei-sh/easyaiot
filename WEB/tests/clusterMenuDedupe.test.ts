import * as assert from 'node:assert/strict'

import {
  resolveRequiredLocalBackRoutes,
  type LocalBackRoute,
} from '../src/router/helper/localBackRouteGuard'

const clusterTitle = '\u96c6\u7fa4\u7ba1\u7406'
const localClusterRoute: LocalBackRoute = {
  path: '/node',
  name: 'NodeManage',
  meta: { title: clusterTitle },
}

assert.deepEqual(
  resolveRequiredLocalBackRoutes(
    [
      {
        path: '/ops/cluster',
        name: 'BackendCluster',
        meta: { title: clusterTitle },
      },
    ],
    [localClusterRoute],
  ),
  [],
  'Back-end permission mode should not add the local cluster-management route when the server already provides a cluster-management menu.',
)

assert.deepEqual(
  resolveRequiredLocalBackRoutes(
    [
      {
        path: '/system/node',
        name: clusterTitle,
      },
    ],
    [localClusterRoute],
  ),
  [],
  'Back-end permission mode should dedupe cluster-management menus even when the server only provides the Chinese route name.',
)

assert.deepEqual(
  resolveRequiredLocalBackRoutes(
    [
      {
        path: '/camera/index',
        name: 'Camera',
        meta: { title: 'Camera' },
      },
    ],
    [localClusterRoute],
  ),
  [localClusterRoute],
  'Back-end permission mode should keep the local cluster-management fallback when the server menu does not provide one.',
)
