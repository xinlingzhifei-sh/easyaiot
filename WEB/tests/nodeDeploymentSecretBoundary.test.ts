import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const source = readFileSync(
  resolve(repoRoot, 'WEB/src/views/node/utils/constants.ts'),
  'utf8',
)

assert.doesNotMatch(
  source,
  /export ZLM_SECRET\s*=\s*["'][^$]/,
  '媒体节点部署脚本不得内置 ZLM secret。',
)
assert.ok(
  source.includes(': "\\${ZLM_SECRET:?'),
  '媒体节点部署脚本必须要求操作者从安全渠道注入 ZLM_SECRET。',
)
