import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)
const devicePlay = readFileSync(
  fileURLToPath(new URL('../src/views/camera/utils/devicePlay.ts', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export const reportDevicePlayReady/,
  'Camera API should expose play-ready access-state reporting.',
)
assert.match(
  cameraApi,
  /export const reportDevicePlayError/,
  'Camera API should expose play-error access-state reporting.',
)
assert.match(
  cameraApi,
  /access-state\/play/,
  'Camera API should call the unified play-state report route.',
)
assert.match(
  devicePlay,
  /reportDevicePlayReady/,
  'Device play helpers should report successful playback readiness.',
)
assert.match(
  devicePlay,
  /reportDevicePlayError/,
  'Device play helpers should report playback failures.',
)
assert.match(
  devicePlay,
  /gb28181VirtualDeviceId/,
  'GB28181 playback reports should target the synced virtual device id.',
)
