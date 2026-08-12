import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export interface RtmpIngestUrlInfo/,
  'Camera API should type the signed RTMP ingest URL response.',
)
assert.match(
  cameraApi,
  /export const issueRtmpIngestUrl/,
  'Camera API should expose signed RTMP ingest URL issuance.',
)
assert.match(
  cameraApi,
  /rtmp-ingest-url/,
  'RTMP ingest URL API should call the backend route.',
)
assert.match(
  cameraApi,
  /export const rotateRtmpIngestToken/,
  'Camera API should expose token rotation.',
)
assert.match(
  cameraApi,
  /rtmp-ingest-token\/rotate/,
  'Token rotation API should call the backend route.',
)
