import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)
const cameraColumns = readFileSync(
  fileURLToPath(new URL('../src/views/camera/Data.tsx', import.meta.url)),
  'utf8',
)
const mixedCardList = readFileSync(
  fileURLToPath(new URL('../src/views/camera/components/DeviceMixedCardList/index.vue', import.meta.url)),
  'utf8',
)
const legacyCardList = readFileSync(
  fileURLToPath(new URL('../src/views/camera/components/VideoCardList/index.vue', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export interface DeviceAccessStateSummary/,
  'Camera API types should expose the unified access-state summary.',
)
assert.match(
  cameraApi,
  /access_state\?: DeviceAccessStateSummary/,
  'DeviceInfo should include access_state for device-list UI rendering.',
)
assert.match(
  cameraColumns,
  /function renderAccessState/,
  'Camera table columns should render a unified access-state cell.',
)
assert.match(
  cameraColumns,
  /dataIndex: 'access_state'/,
  'Camera table should include the access_state column.',
)
assert.match(
  cameraColumns,
  /play_ready/,
  'Access-state rendering should show play readiness.',
)
assert.match(
  cameraColumns,
  /ai_ready/,
  'Access-state rendering should show AI readiness.',
)
assert.match(
  cameraColumns,
  /reason_message/,
  'Access-state rendering should surface the error or blocking reason.',
)
for (const source of [mixedCardList, legacyCardList]) {
  assert.match(
    source,
    /renderCardAccessState/,
    'Camera card lists should render the unified access-state summary.',
  )
  assert.match(
    source,
    /access_state/,
    'Camera card lists should read access_state from each device.',
  )
  assert.match(
    source,
    /play_ready/,
    'Camera card lists should show play readiness.',
  )
  assert.match(
    source,
    /ai_ready/,
    'Camera card lists should show AI readiness.',
  )
  assert.match(
    source,
    /reason_message/,
    'Camera card lists should surface the error or blocking reason.',
  )
}
