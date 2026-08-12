import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const cameraApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/camera.ts', import.meta.url)),
  'utf8',
)
const rtcPlayer = readFileSync(
  fileURLToPath(new URL('../src/components/Player/module/rtcPlayer.vue', import.meta.url)),
  'utf8',
)
const playerShell = readFileSync(
  fileURLToPath(new URL('../src/components/Player/index.vue', import.meta.url)),
  'utf8',
)
const zlmClient = readFileSync(
  fileURLToPath(new URL('../public/static/js/ZLMRTCClient.js', import.meta.url)),
  'utf8',
)

assert.match(
  cameraApi,
  /export const getWebrtcNatConfig/,
  'Camera API should expose WebRTC NAT config.',
)
assert.match(
  cameraApi,
  /webrtc\/nat-config/,
  'Camera API should call the WebRTC NAT config route.',
)
assert.match(
  rtcPlayer,
  /getWebrtcNatConfig/,
  'RTC player should load WebRTC NAT config before playback.',
)
assert.match(
  rtcPlayer,
  /pcConfig/,
  'RTC player should pass peer-connection config to ZLMRTCClient.',
)
assert.match(
  rtcPlayer,
  /resolveWebrtcPlayUrl/,
  'RTC player should normalize WebRTC play URLs from NAT config.',
)
assert.match(
  rtcPlayer,
  /public_host/,
  'RTC player should rewrite the WebRTC play URL host when public_host is configured.',
)
assert.match(
  rtcPlayer,
  /prefer_wss|require_secure_context/,
  'RTC player should prefer secure RTC URLs when NAT config requires secure playback.',
)
assert.match(
  rtcPlayer,
  /reportDevicePlayError/,
  'RTC player should report WebRTC playback failures to the access-state center.',
)
assert.match(
  rtcPlayer,
  /reportDevicePlayReady/,
  'RTC player should report WebRTC playback success to the access-state center.',
)
assert.match(
  rtcPlayer,
  /reportWebrtcPlayReady/,
  'RTC player should normalize successful remote streams before reporting play_ready.',
)
assert.match(
  rtcPlayer,
  /webrtc_remote_stream_ready/,
  'RTC player should use a stable reason code for WebRTC remote stream readiness.',
)
assert.match(
  rtcPlayer,
  /webrtc.remote.stream/,
  'RTC player should use a stable source event for WebRTC remote stream readiness.',
)
assert.match(
  rtcPlayer,
  /webrtc_ice_candidate_error/,
  'RTC player should normalize ICE candidate failures for access-state errors.',
)
assert.match(
  rtcPlayer,
  /webrtc_offer_answer_failed/,
  'RTC player should normalize offer-answer failures for access-state errors.',
)
assert.match(
  rtcPlayer,
  /protocol:\s*'webrtc'/,
  'RTC player error reports should target the WebRTC access protocol.',
)
assert.match(
  rtcPlayer,
  /source_event:\s*sourceEvent/,
  'RTC player error reports should carry the WebRTC source event for diagnostics.',
)
assert.match(
  playerShell,
  /:device-id="deviceId"/,
  'Player shell should pass device id into the RTC player.',
)
assert.match(
  zlmClient,
  /new RTCPeerConnection\(this\.options\.pcConfig \|\| null\)/,
  'ZLMRTCClient should construct RTCPeerConnection with injected pcConfig.',
)
