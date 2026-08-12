import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)
const cameraIndex = readFileSync(
  fileURLToPath(new URL('../src/views/camera/index.vue', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export interface DeviceAccessStateEvent/,
  'Camera API should type device access event history rows.',
)

assert.match(
  cameraApi,
  /export interface DeviceAccessEventsResult/,
  'Camera API should type the access-state events response.',
)

assert.match(
  cameraApi,
  /export const getDeviceAccessEvents/,
  'Camera API should expose access-state event history loading.',
)

assert.match(
  cameraApi,
  /access-state\/events/,
  'Camera API should call the access-state events endpoint.',
)

assert.match(
  cameraApi,
  /protocol/,
  'Camera API should allow filtering access-state events by protocol.',
)

assert.match(
  cameraIndex,
  /getDeviceAccessEvents/,
  'Camera page should load device access event history from the API.',
)

assert.match(
  cameraIndex,
  /openAccessEventHistory/,
  'Camera page should expose an action for opening access-state event history.',
)

assert.match(
  cameraIndex,
  /accessEventHistoryVisible/,
  'Camera page should keep modal visibility for access-state event history.',
)

assert.match(
  cameraIndex,
  /accessEventHistoryRows/,
  'Camera page should render access-state event rows.',
)

assert.match(
  cameraIndex,
  /reason_message/,
  'Camera page should show normalized event failure reasons.',
)

assert.match(
  cameraIndex,
  /source_event/,
  'Camera page should show event source names for audit tracing.',
)
