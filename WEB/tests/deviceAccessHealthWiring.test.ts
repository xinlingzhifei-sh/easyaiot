import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export interface DeviceAccessHealthSnapshot/,
  'Camera API should type the device access health snapshot.',
)

assert.match(
  cameraApi,
  /export const getDeviceAccessHealth/,
  'Camera API should expose access-state health polling.',
)

assert.match(
  cameraApi,
  /access-state\/health/,
  'Camera API should call the access-state health endpoint.',
)

assert.match(
  cameraApi,
  /stale_after_seconds/,
  'Camera API should pass the stale threshold parameter expected by the backend.',
)
