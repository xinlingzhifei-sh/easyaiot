import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraIndex = readFileSync(
  fileURLToPath(new URL('../src/views/camera/index.vue', import.meta.url)),
  'utf8',
)

assert.match(
  cameraIndex,
  /getDeviceAccessHealth/,
  'Camera device list should load the access-state health snapshot.',
)

assert.match(
  cameraIndex,
  /accessHealth/,
  'Camera device list should keep access-state health in view state.',
)

assert.match(
  cameraIndex,
  /access-health-banner/,
  'Camera device list should render a compact access-state health banner.',
)

assert.match(
  cameraIndex,
  /alert_count/,
  'Camera device list health banner should show the current alert count.',
)
