import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const streamForwardApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/stream_forward.ts', import.meta.url)),
  'utf8',
)
const directRtspPanel = readFileSync(
  fileURLToPath(new URL('../src/views/camera/components/DeviceCreate/panels/DirectRtspPanel.vue', import.meta.url)),
  'utf8',
)

assert.match(
  streamForwardApi,
  /export const ensureEdgeStreamForwardTask/,
  'Stream-forward API should expose Edge RTSP task creation.',
)
assert.match(
  streamForwardApi,
  /ensure-edge-task/,
  'Edge RTSP task API should call the backend route.',
)
assert.match(
  streamForwardApi,
  /export const reconcileEdgeStreamForwardTask/,
  'Stream-forward API should expose Edge command reconciliation.',
)
assert.match(
  streamForwardApi,
  /reconcile-edge-task/,
  'Edge command reconciliation should call the backend route.',
)
assert.match(
  streamForwardApi,
  /timeout_seconds/,
  'Edge command reconciliation should pass timeout settings.',
)
assert.match(
  streamForwardApi,
  /max_attempts/,
  'Edge command reconciliation should pass retry limit settings.',
)
assert.match(
  directRtspPanel,
  /listScheduleNodes/,
  'Direct RTSP creation should load selectable Edge/compute nodes.',
)
assert.match(
  directRtspPanel,
  /edge_node_id/,
  'Direct RTSP creation should include an Edge node selector field.',
)
assert.match(
  directRtspPanel,
  /access_mode/,
  'Direct RTSP creation should let the user choose local or Edge access mode.',
)
assert.match(
  directRtspPanel,
  /ensureEdgeStreamForwardTask/,
  'Direct RTSP creation should enqueue Edge outbound access when Edge mode is selected.',
)
assert.match(
  directRtspPanel,
  /reconcileEdgeStreamForwardTask/,
  'Direct RTSP creation should let the user reconcile an Edge command after enqueue.',
)
assert.match(
  directRtspPanel,
  /ensureDeviceStreamForwardTask/,
  'Direct RTSP creation should keep the existing local stream-forward path.',
)
assert.match(
  directRtspPanel,
  /edgeCommandStatus/,
  'Direct RTSP creation should keep visible Edge command status after enqueue.',
)
assert.match(
  directRtspPanel,
  /edgeCommandError/,
  'Direct RTSP creation should surface Edge command enqueue errors.',
)
assert.match(
  directRtspPanel,
  /commandId/,
  'Direct RTSP creation should show the queued Edge command id.',
)
assert.match(
  directRtspPanel,
  /commandStatus/,
  'Direct RTSP creation should show the queued Edge command status.',
)
assert.match(
  directRtspPanel,
  /action/,
  'Direct RTSP creation should show the latest Edge command reconciliation action.',
)
assert.match(
  directRtspPanel,
  /edgeCommandLoading/,
  'Direct RTSP creation should expose a loading state while checking Edge command status.',
)
assert.match(
  directRtspPanel,
  /catch \(error: unknown\)/,
  'Direct RTSP creation should handle Edge command errors explicitly.',
)
