import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const clusterMetricsWs = readFileSync(
  fileURLToPath(new URL('../src/views/node/utils/clusterMetricsWs.ts', import.meta.url)),
  'utf8',
)

assert.match(
  clusterMetricsWs,
  /import\s+\{\s*getAccessToken\s*\}\s+from\s+['"]@\/utils\/auth['"]/,
  'Cluster metrics WebSocket should use the same app auth cache as normal API requests.',
)

assert.doesNotMatch(
  clusterMetricsWs,
  /localStorage\.getItem\(['"]jwt_token['"]\)/,
  'Cluster metrics WebSocket must not read the legacy jwt_token key because logged-in users store tokens in the app auth cache.',
)
