import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const deviceCreate = readFileSync(
  fileURLToPath(new URL('../src/views/camera/components/DeviceCreate/index.vue', import.meta.url)),
  'utf8',
)
const deviceCreateOptions = readFileSync(
  fileURLToPath(new URL('../src/views/camera/utils/deviceCreateOptions.ts', import.meta.url)),
  'utf8',
)
const rtmpPanelPath = '../src/views/camera/components/DeviceCreate/panels/RtmpIngestPanel.vue'

assert.match(
  deviceCreate,
  /RtmpIngestPanel/,
  'Device create should wire a public RTMP ingest panel.',
)
assert.match(
  deviceCreate,
  /rtmp_push/,
  'Device create should expose a RTMP public-push tab.',
)
assert.match(
  deviceCreateOptions,
  /rtmp_push/,
  'Device create method options should include RTMP public push.',
)

const rtmpPanel = readFileSync(fileURLToPath(new URL(rtmpPanelPath, import.meta.url)), 'utf8')

assert.match(
  rtmpPanel,
  /issueRtmpIngestUrl/,
  'RTMP ingest panel should issue per-device signed push URLs.',
)
assert.match(
  rtmpPanel,
  /rotateRtmpIngestToken/,
  'RTMP ingest panel should allow token version rotation.',
)
assert.match(
  rtmpPanel,
  /tokenRotatedNotice/,
  'RTMP ingest panel should keep a visible notice after token rotation.',
)
assert.match(
  rtmpPanel,
  /旧推流地址已作废/,
  'RTMP ingest panel should tell operators that old push URLs are revoked after rotation.',
)
assert.match(
  rtmpPanel,
  /registerDevice/,
  'RTMP ingest panel should register a device before issuing its push URL.',
)
assert.match(
  rtmpPanel,
  /push_url/,
  'RTMP ingest panel should display the generated push URL.',
)
assert.match(
  rtmpPanel,
  /expires_at/,
  'RTMP ingest panel should display URL expiry.',
)
assert.match(
  rtmpPanel,
  /token_version/,
  'RTMP ingest panel should display token version.',
)
