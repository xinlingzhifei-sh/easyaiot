import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const source = readFileSync(fileURLToPath(new URL('../src/api/device/gb28181.ts', import.meta.url)), 'utf8')
const queryVideoListSource = source.slice(
  source.indexOf('export const queryVideoList'),
  source.indexOf('export const queryAllVideoList'),
)

assert.match(
  queryVideoListSource,
  /pageNo\??:\s*number/,
  'GB28181 device list should map the table pageNo/pageSize parameters to WVP page/count query parameters.',
)

assert.match(
  queryVideoListSource,
  /requestParams\.page\s*=\s*params\.pageNo/,
  'GB28181 device list should send pageNo as the page query parameter.',
)
