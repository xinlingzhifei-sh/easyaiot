import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const devicePlaySource = readFileSync(
  fileURLToPath(new URL('../src/views/camera/utils/devicePlay.ts', import.meta.url)),
  'utf8',
)

// 启用 AI 时直接播 /ai，原始流只作播放器回退（AI_PLAY_FALLBACK_MS 内切回）。
// 先播原始流、后台等 AI 的旧做法会让空 /live 把 SRS 挂住，分屏一直加载中（见 pickDirectPlayUrls 注释）。
assert.match(
  devicePlaySource,
  /return \{\s*url: aiUrl,\s*fallbackUrl: videoUrl,\s*preferAi: true,/,
  'AI playback should start the AI stream directly and keep the original stream as the player fallback.',
)

assert.match(
  devicePlaySource,
  /export function schedulePendingAiStreamUpgrade[\s\S]*const probeMs = options\?\.probeMs \?\? AI_STREAM_PROBE_MS;[\s\S]*const ready = await probeStreamPlayable\(ai, probeMs\);\s*if \(!ready \|\| !shouldUpgrade\(\)\) return;\s*onUpgrade\(\)/,
  'AI playback should probe in the background and upgrade only after the AI stream becomes playable.',
)

assert.match(
  devicePlaySource,
  /export const AI_PLAY_FALLBACK_MS = 2500;/,
  'AI playback fallback should not leave monitor cells loading for a full minute.',
)

assert.match(
  devicePlaySource,
  /const aiPlayerEngine = pickLivePlayerEngine\(\{[\s\S]*url,[\s\S]*videoCodec: wvpSource\.videoCodec[\s\S]*\}\);[\s\S]*playerEngine: aiPlayerEngine/,
  'GB28181 AI FLV streams must use a player engine derived from the AI URL instead of inheriting a WebRTC fallback engine.',
)

assert.match(
  devicePlaySource,
  /if \(!isAiStreamPlayUrl\(url\) && !pendingAiUrl && wvpSource\.url\) \{\s*return wvpSource;\s*\}/,
  'GB28181 AI fallback should use the fresh WVP play/start source instead of stale synced device http_stream values.',
)

const dashboardMonitorSource = readFileSync(
  fileURLToPath(
    new URL('../src/views/dashboard/monitor/components/VideoMonitor.vue', import.meta.url),
  ),
  'utf8',
)
const splitScreenMonitorSource = readFileSync(
  fileURLToPath(
    new URL('../src/views/camera/components/SplitScreenMonitor/MonitorPanel.vue', import.meta.url),
  ),
  'utf8',
)

for (const source of [dashboardMonitorSource, splitScreenMonitorSource]) {
  assert.doesNotMatch(
    source,
    /ZLM 已收到推流/,
    'AI fallback warning should not imply the AI stream must appear in ZLM when the AI output is served by the media server /ai path.',
  )
  assert.match(
    source,
    /媒体服务器已收到 AI 推流/,
    'AI fallback warning should point operators to the actual AI media output path.',
  )
}
