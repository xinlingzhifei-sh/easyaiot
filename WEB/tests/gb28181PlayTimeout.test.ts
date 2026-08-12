import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const source = readFileSync(fileURLToPath(new URL('../src/api/device/gb28181.ts', import.meta.url)), 'utf8')
const playByDeviceAndChannelSource = source.slice(
  source.indexOf('export const playByDeviceAndChannel'),
  source.indexOf('export const stopPlay', source.indexOf('export const playByDeviceAndChannel')),
)

assert.match(
  source,
  /const\s+GB28181_PLAY_REQUEST_TIMEOUT_MS\s*=\s*60\s*\*\s*1000/,
  'GB28181 play/start should use a dedicated 60s timeout instead of the global 10s HTTP timeout.',
)

assert.match(
  playByDeviceAndChannelSource,
  /timeout:\s*GB28181_PLAY_REQUEST_TIMEOUT_MS/,
  'GB28181 live play/start should pass the dedicated timeout to the request.',
)
