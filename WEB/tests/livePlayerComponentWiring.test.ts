import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const sharedPlayer = readFileSync(
  fileURLToPath(new URL('../src/components/Player/module/jessibuca.vue', import.meta.url)),
  'utf8',
)
const appIndex = readFileSync(
  fileURLToPath(new URL('../index.html', import.meta.url)),
  'utf8',
)
const dialogPlayer = readFileSync(
  fileURLToPath(new URL('../src/components/VideoPlayer/DialogPlayer.vue', import.meta.url)),
  'utf8',
)
const rtcPlayer = readFileSync(
  fileURLToPath(new URL('../src/components/VideoPlayer/rtcPlayer.vue', import.meta.url)),
  'utf8',
)
const monitorPanel = readFileSync(
  fileURLToPath(
    new URL('../src/views/camera/components/SplitScreenMonitor/MonitorPanel.vue', import.meta.url),
  ),
  'utf8',
)
const videoMonitor = readFileSync(
  fileURLToPath(
    new URL('../src/views/dashboard/monitor/components/VideoMonitor.vue', import.meta.url),
  ),
  'utf8',
)
const easyPlayer = readFileSync(
  fileURLToPath(new URL('../src/components/VideoPlayer/EasyPlayer.vue', import.meta.url)),
  'utf8',
)
const zlmRepairScript = readFileSync(
  fileURLToPath(new URL('../../.scripts/docker/fix_zlmediakit.sh', import.meta.url)),
  'utf8',
)
const zlmClusterTemplate = readFileSync(
  fileURLToPath(new URL('../../.scripts/media-cluster/zlm/config.ini.template', import.meta.url)),
  'utf8',
)

assert.match(
  sharedPlayer,
  /import EasyPlayer from ["']@\/components\/VideoPlayer\/EasyPlayer\.vue["']/,
  'The shared live player wrapper should import EasyPlayer for WASM decoding.',
)
assert.match(
  sharedPlayer,
  /import RtcPlayer from ["']@\/components\/VideoPlayer\/rtcPlayer\.vue["']/,
  'The shared live player wrapper should import RtcPlayer for WebRTC fallback sources.',
)
assert.match(
  sharedPlayer,
  /shouldUseWasmLivePlayer/,
  'The shared live player wrapper should use the codec/player strategy.',
)
assert.match(
  sharedPlayer,
  /<EasyPlayer[\s\S]*v-(?:else-)?if="useEasyWasm"/,
  'The shared live player wrapper should render EasyPlayer for codecs that require WASM playback.',
)
assert.match(
  sharedPlayer,
  /@playing="onEasyWasmPlaying"/,
  'The shared live player wrapper should wait for EasyPlayer to report real playback before marking the slot as playing.',
)
assert.match(
  sharedPlayer,
  /emits: \["stream-error", "playing"\]/,
  'The shared live player wrapper should emit playback success so monitor AI fallback timers do not rely on stale child refs.',
)
assert.match(
  sharedPlayer,
  /<RtcPlayer[\s\S]*@playing="onWebRtcPlaying"/,
  'The shared live player wrapper should also surface WebRTC playback success.',
)
assert.match(
  sharedPlayer,
  /onEasyWasmPlaying\(\)[\s\S]*this\.markPlaying\(\)/,
  'The shared live player wrapper should expose EasyWasm playback only after the first frame path starts.',
)
assert.match(
  sharedPlayer,
  /<RtcPlayer[\s\S]*v-if="useWebRtc"[\s\S]*@stream-error="\$emit\('stream-error', \$event\)"/,
  'The shared live player wrapper should render RtcPlayer when fallback selects a WebRTC source.',
)
assert.match(
  appIndex,
  /<script[^>]+src=["']\/static\/js\/ZLMRTCClient\.js["'][^>]*><\/script>/,
  'The app shell must load ZLMRTCClient before realtime WebRTC playback is selected.',
)
assert.match(
  sharedPlayer,
  /<video[\s\S]*v-(?:else-)?if="useNativeVideo"[\s\S]*class="native-video-player"/,
  'The shared live player wrapper should render a native video element for HEVC fMP4 when the browser supports it.',
)
assert.match(
  sharedPlayer,
  /useNativeVideo\(\)[\s\S]*playerEngine === 'native'/,
  'The shared live player wrapper should honor the native player engine selected by the stream strategy.',
)
assert.match(
  sharedPlayer,
  /useWebRtc\(\)[\s\S]*playerEngine === 'webrtc'/,
  'The shared live player wrapper should honor the WebRTC player engine selected by the stream strategy.',
)
assert.match(
  sharedPlayer,
  /playerEngine/,
  'The shared live player wrapper should accept an explicit player engine override.',
)
assert.match(
  sharedPlayer,
  /videoCodec/,
  'The shared live player wrapper should accept explicit codec metadata.',
)
assert.match(
  sharedPlayer,
  /shouldUseWasmLivePlayer\(\{[\s\S]*videoCodec: this\.videoCodec/,
  'The shared live player wrapper should use explicit codec metadata when choosing the engine.',
)
assert.match(
  sharedPlayer,
  /:decodeType="easyWasmDecodeType"/,
  'The shared live player wrapper should pass a codec-aware decode type to EasyPlayer.',
)
assert.match(
  sharedPlayer,
  /nativeVideoUrl = target/,
  'The shared live player wrapper should pass the signed stream URL to the native video element.',
)
assert.doesNotMatch(
  sharedPlayer,
  /this\.playUrl !== originalPlayUrl \|\| !this\.jessibuca/,
  'The shared live player wrapper must not abort signed EasyWasm/native playback just because there is no Jessibuca instance.',
)

assert.match(
  dialogPlayer,
  /pickWvpPlaySource/,
  'DialogPlayer should preserve WVP codec metadata when choosing the play source.',
)
assert.match(
  dialogPlayer,
  /pickWvpPlaySources/,
  'DialogPlayer should keep all playable WVP sources so WebRTC can fall back to FLV.',
)
assert.match(
  dialogPlayer,
  /import RtcPlayer from ["']@\/components\/VideoPlayer\/rtcPlayer\.vue["']/,
  'DialogPlayer should import the WebRTC player for rtcs playback.',
)
assert.match(
  dialogPlayer,
  /<RtcPlayer[\s\S]*v-if="[^"]*state\.playerEngine === 'webrtc'"/,
  'DialogPlayer should render the WebRTC player when the selected source is rtcs/rtc.',
)
assert.match(
  dialogPlayer,
  /@stream-error="handleStreamError"/,
  'DialogPlayer should listen for WebRTC failures and fall back to another source.',
)
assert.match(
  dialogPlayer,
  /:playerEngine="state\.playerEngine"/,
  'DialogPlayer should pass the selected player engine to the shared live player wrapper.',
)
assert.match(
  dialogPlayer,
  /state\.playSources\.find\(\(source\) => source\.url === value\)/,
  'DialogPlayer should switch the active player source when the URL dropdown changes.',
)
assert.match(
  rtcPlayer,
  /WEBRTC_ON_CONNECTION_STATE_CHANGE/,
  'RtcPlayer should surface peer connection failures so DialogPlayer can fall back to FLV.',
)
assert.match(
  rtcPlayer,
  /first-frame-timeout/,
  'RtcPlayer should surface a no-first-frame timeout so DialogPlayer can fall back to FLV.',
)
assert.match(
  rtcPlayer,
  /ref="video"/,
  'RtcPlayer should bind ZLMRTCClient to its own video element so dashboard cells do not fight over one global id.',
)
assert.match(
  rtcPlayer,
  /webrtcPlayer: null/,
  'RtcPlayer should keep the WebRTC endpoint on the component instance.',
)
assert.doesNotMatch(
  rtcPlayer,
  /let webrtcPlayer = null/,
  'RtcPlayer should not share one module-level WebRTC endpoint across all video cells.',
)

assert.match(
  monitorPanel,
  /:playerEngine="state\.playCells\[i - 1\]!\.playerEngine \|\| ''"/,
  'Split-screen monitor cells should pass the selected player engine to the shared player.',
)
assert.match(
  monitorPanel,
  /:videoCodec="state\.playCells\[i - 1\]!\.videoCodec \|\| ''"/,
  'Split-screen monitor cells should pass codec metadata to the shared player.',
)
assert.match(
  monitorPanel,
  /@playing="handleCellPlaying\(i - 1, \$event\)"/,
  'Split-screen monitor cells should cancel the AI warm-up fallback as soon as the active URL starts playing.',
)
assert.match(
  monitorPanel,
  /playerEngine\?: string \| null/,
  'Split-screen monitor cells should persist player engine metadata across reloads.',
)
assert.match(
  monitorPanel,
  /videoCodec\?: string \| null/,
  'Split-screen monitor cells should persist codec metadata across reloads.',
)
assert.match(
  monitorPanel,
  /playSources\?: WvpPlaySourceOption\[\] \| null/,
  'Split-screen monitor cells should retain GB28181 candidate sources for stream-error fallback.',
)
assert.match(
  monitorPanel,
  /payload\.playSources/,
  'Split-screen monitor should store WVP candidate sources returned by GB28181 play/start.',
)
assert.match(
  monitorPanel,
  /findNextPlaySource\(cell\)/,
  'Split-screen monitor should try the next GB28181 source when the active stream errors.',
)

assert.match(
  videoMonitor,
  /:playerEngine="video\.playerEngine \|\| ''"/,
  'Dashboard monitor cells should pass the selected player engine to the shared player.',
)
assert.match(
  videoMonitor,
  /:videoCodec="video\.videoCodec \|\| ''"/,
  'Dashboard monitor cells should pass codec metadata to the shared player.',
)
assert.match(
  videoMonitor,
  /@stream-error="handleVideoStreamError\(index\)"/,
  'Dashboard monitor cells should listen for player failures instead of leaving videos loading forever.',
)
assert.match(
  videoMonitor,
  /@playing="handleVideoPlaying\(index, \$event\)"/,
  'Dashboard monitor cells should cancel the AI warm-up fallback as soon as the active URL starts playing.',
)
assert.match(
  videoMonitor,
  /playSources\?: WvpPlaySourceOption\[\] \| null/,
  'Dashboard monitor cells should retain GB28181 candidate sources for stream-error fallback.',
)
assert.match(
  videoMonitor,
  /findNextPlaySource\(slot\)/,
  'Dashboard monitor should try the next GB28181 source when the active stream errors.',
)

assert.match(
  easyPlayer,
  /:id="playerId"/,
  'EasyPlayer should use a per-instance DOM id instead of the old global easyplayer id.',
)
assert.match(
  easyPlayer,
  /window\.WasmPlayer/,
  'EasyPlayer should use the globally loaded EasyWasmPlayer constructor safely.',
)
assert.match(
  easyPlayer,
  /decodeType/,
  'EasyPlayer should expose EasyWasmPlayer decodeType so H265 can force software decoding.',
)
assert.match(
  easyPlayer,
  /emits: \['stream-error', 'playing'\]/,
  'EasyPlayer should emit a playback event when WASM starts producing media.',
)
assert.match(
  easyPlayer,
  /firstFrameTimer: null/,
  'EasyPlayer should keep a per-instance first-frame timeout timer.',
)
assert.match(
  easyPlayer,
  /emitStreamError\('first-frame-timeout'/,
  'EasyPlayer should surface no-first-frame stalls instead of leaving split-screen cells loading forever.',
)
assert.match(
  rtcPlayer,
  /emits: \['stream-error', 'playing'\]/,
  'RtcPlayer should emit playback success after receiving a remote WebRTC stream.',
)
assert.match(
  rtcPlayer,
  /WEBRTC_ON_REMOTE_STREAMS[\s\S]*this\.\$emit\('playing',/,
  'RtcPlayer should surface WebRTC remote-stream readiness to the shared player wrapper.',
)

assert.match(
  zlmRepairScript,
  /\[rtmp\][\s\S]*directProxy=1[\s\S]*enhanced=0/,
  'ZLMediaKit repair config should disable enhanced FLV for EasyWasmPlayer H265 compatibility.',
)
assert.match(
  zlmClusterTemplate,
  /\[rtmp\][\s\S]*directProxy=1[\s\S]*enhanced=0/,
  'ZLMediaKit cluster template should disable enhanced FLV for EasyWasmPlayer H265 compatibility.',
)
