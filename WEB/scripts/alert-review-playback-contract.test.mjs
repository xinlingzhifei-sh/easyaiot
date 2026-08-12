import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFileSync } from 'node:fs'
import { build } from 'esbuild'

const playbackSource = readFileSync('src/utils/alertRecordPlayback.ts', 'utf8')
const workbenchSource = readFileSync('src/views/alert/components/AlertReviewWorkbench.vue', 'utf8')
const alertReviewApiSource = readFileSync('src/api/supervision/alertReview.ts', 'utf8')
const alertPageSource = readFileSync('src/views/alert/index.vue', 'utf8')
const dialogPlayerSource = readFileSync('src/components/VideoPlayer/DialogPlayer.vue', 'utf8')
const jessibucaSource = readFileSync('src/components/Player/module/jessibuca.vue', 'utf8')
const snapApiSource = readFileSync('src/api/device/snap.ts', 'utf8')
const snapGallerySource = readFileSync('src/views/camera/components/SnapSpace/SnapSpaceImageGallery.vue', 'utf8')

assert.match(playbackSource, /seek_time\?: string \| null/)
assert.match(playbackSource, /playback_offset_seconds\?: number \| null/)
assert.match(playbackSource, /function resolvePlaybackSeekContext/)
assert.match(playbackSource, /playback_offset_seconds: seekContext\.playbackOffsetSeconds/)
assert.match(playbackSource, /record\.playback_offset_seconds != null/)

const detailStreamPlayback = workbenchSource.match(
  /async function openDetailStreamEntry[\s\S]*?\n}\n\nasync function openUnifiedTimelineEntry/,
)?.[0] || ''
assert.match(detailStreamPlayback, /record_start_time: entry\.recordStartTime/)
assert.match(detailStreamPlayback, /playback_offset_seconds: entry\.playbackOffsetSeconds/)
assert.doesNotMatch(detailStreamPlayback, /reviewSegment/)
assert.match(workbenchSource, /record_start_time: segment\.startTime/)
assert.match(workbenchSource, /device_id: selectedItem\.value\?\.cameraId \|\| selectedItem\.value\?\.deviceId/)
assert.match(workbenchSource, /device_id: item\.cameraId \|\| item\.deviceId/)
assert.match(alertReviewApiSource, /recordStartTime\?: string \| null/)
assert.match(alertReviewApiSource, /playbackOffsetSeconds\?: number \| null/)
assert.match(
  alertReviewApiSource,
  /export interface AlertReviewMediaReadQuery \{[\s\S]*?reviewCaseId\?: number[\s\S]*?operatorUserId\?: number[\s\S]*?allowedCameraIds\?: string\[\][\s\S]*?\}/,
)
for (const functionName of [
  'getAlertReviewTimeline',
  'getAlertReviewDetailStream',
  'getAlertReviewRecordCoverage',
]) {
  const mediaReadApi = alertReviewApiSource.match(
    new RegExp(`export function ${functionName}\\(reviewItemId: number, params\\?: AlertReviewMediaReadQuery\\) \\{[\\s\\S]*?\\n\\}`),
  )?.[0] || ''
  assert.match(mediaReadApi, /params: params \?\? \{\}/, `${functionName} must forward the complete media-read scope`)
}
assert.match(workbenchSource, /function mediaReadScope\(item: AlertReviewItem\)/)
assert.match(workbenchSource, /reviewCaseId: activeCase\.value\?\.id/)
assert.match(workbenchSource, /operatorUserId: filters\.reviewerUserId \|\| undefined/)
assert.match(workbenchSource, /allowedCameraIds: cameraId \? \[cameraId\] : undefined/)
assert.match(workbenchSource, /function selectReviewItem\(item: AlertReviewItem\)/)
assert.match(workbenchSource, /selectedItem\.value\?\.id !== item\.id/)
assert.match(workbenchSource, /activeCase\.value = null/)
assert.match(workbenchSource, /async function loadRecordCoverageForItem\(item: AlertReviewItem\)/)
assert.match(workbenchSource, /await loadItemCase\(item\)[\s\S]*?await loadRecordCoverage\(item\)/)
assert.match(workbenchSource, /getAlertReviewTimeline\(target\.id, mediaReadScope\(target\)\)/)
assert.match(workbenchSource, /getAlertReviewDetailStream\(target\.id, mediaReadScope\(target\)\)/)
assert.match(workbenchSource, /getAlertReviewRecordCoverage\(target\.id, mediaReadScope\(target\)\)/)
assert.match(workbenchSource, /if \(isSelectedReviewItem\(reviewItemId\)\)\s+timeline\.value = nextTimeline/)
assert.match(workbenchSource, /if \(isSelectedReviewItem\(reviewItemId\)\)\s+detailStream\.value = nextDetailStream/)
assert.match(workbenchSource, /if \(isSelectedReviewItem\(reviewItemId\)\)\s+coverage\.value = nextCoverage/)
assert.match(workbenchSource, /const reviewCaseId = activeCase\.value\.id[\s\S]*?if \(activeCase\.value\?\.id !== reviewCaseId\)\s+return[\s\S]*?caseTimeline\.value = nextCaseTimeline/)
assert.match(workbenchSource, /async function loadEvidenceAudit\(reviewCaseId = activeCase\.value\?\.id\)[\s\S]*?if \(activeCase\.value\?\.id === reviewCaseId\)\s+evidenceAudit\.value = nextEvidenceAudit/)
const caseTimelinePlayback = workbenchSource.match(
  /async function openCaseTimelineEntry[\s\S]*?\n}\n\nasync function guardCaseTimelineMediaAccess/,
)?.[0] || ''
assert.match(caseTimelinePlayback, /record_start_time: entry\.recordStartTime/)
assert.match(caseTimelinePlayback, /playback_offset_seconds: entry\.playbackOffsetSeconds/)
assert.doesNotMatch(caseTimelinePlayback, /reviewSegment|activeCase\.value\?\.startTime/)
assert.match(
  workbenchSource,
  /createAlertReviewEvidenceExportJob\(activeCase\.value\.id, \{[\s\S]*?format: 'mp4'/,
  'ordinary evidence export must request a real MP4 artifact',
)

assert.match(alertPageSource, /seek_time: record\['seek_time'\]/)
assert.match(alertPageSource, /record_start_time: record\['record_start_time'\]/)
assert.match(alertPageSource, /playback_offset_seconds: record\['playback_offset_seconds'\]/)
assert.match(alertPageSource, /video_url: record\['video_url'\]/)
assert.match(alertPageSource, /url: record\['url'\]/)

assert.match(dialogPlayerSource, /seekOffsetSeconds: -1/)
assert.match(dialogPlayerSource, /record\['playback_offset_seconds'\] != null/)
assert.match(dialogPlayerSource, /data-testid="alert-review-dialog-player-stage"/)
assert.match(dialogPlayerSource, /:data-seek-time="state\.seekTime"/)
assert.match(dialogPlayerSource, /:data-playback-offset-seconds="state\.seekOffsetSeconds"/)
assert.match(dialogPlayerSource, /:seekOffsetSeconds="state\.seekOffsetSeconds"/)
assert.match(dialogPlayerSource, /shouldUseNativeSeekPlayback\(streamUrl, state\.seekOffsetSeconds, state\.seekTime\)/)
assert.match(dialogPlayerSource, /seekTime: string/)
assert.match(dialogPlayerSource, /playback_format=mp4/)

assert.match(jessibucaSource, /seekOffsetSeconds:/)
assert.match(jessibucaSource, /applyNativeSeek\(\)/)
assert.match(jessibucaSource, /video\.currentTime = offset/)
assert.match(jessibucaSource, /offset < 0/)

assert.match(snapApiSource, /export const loadSnapImageObjectUrl = async/)
assert.match(snapApiSource, /responseType: 'blob'/)
assert.match(snapGallerySource, /loadSnapImageObjectUrl/)
assert.match(snapGallerySource, /snapImageObjectUrls/)
assert.match(snapGallerySource, /URL\.revokeObjectURL/)
assert.match(snapGallerySource, /onBeforeUnmount/)

const bundledPlayback = await build({
  bundle: true,
  define: {
    'import.meta.env.VITE_GLOB_API_URL': JSON.stringify(
      'https://media.example.test/yfeieye/dev-api',
    ),
  },
  entryPoints: ['src/utils/alertRecordPlayback.ts'],
  format: 'esm',
  platform: 'browser',
  plugins: [
    {
      name: 'alert-record-test-stub',
      setup(buildContext) {
        buildContext.onResolve({ filter: /^@\/hooks\/web\/useMessage$/ }, () => ({
          namespace: 'alert-record-message-test',
          path: 'alert-record-message-test',
        }))
        buildContext.onLoad({ filter: /.*/, namespace: 'alert-record-message-test' }, () => ({
          contents: `
            export function useMessage() {
              return {
                createMessage: {
                  loading() {},
                  destroy() {},
                },
              };
            }
          `,
          loader: 'js',
        }))
        buildContext.onResolve({ filter: /^@\/utils\/alertRecord$/ }, () => ({
          namespace: 'alert-record-test',
          path: 'alert-record-test',
        }))
        buildContext.onLoad({ filter: /.*/, namespace: 'alert-record-test' }, () => ({
          contents: `
            export function resolveAlertVideoUrl(url) {
              return url;
            }
            export async function resolveAlertRecordVideoUrl() {
              globalThis.__alertRecordQueryCalls += 1;
              return 'https://app.example.test/video/alert/record?fallback=1';
            }
          `,
          loader: 'js',
        }))
      },
    },
  ],
  write: false,
})
const playbackModuleUrl = `data:text/javascript;base64,${Buffer.from(
  bundledPlayback.outputFiles[0].text,
).toString('base64')}`
const { playAlertRecordInModal } = await import(playbackModuleUrl)

globalThis.window = {
  location: {
    origin: 'https://app.example.test',
  },
}

function signedPlaybackUrl(origin, path, cameraId = 'camera-01') {
  const url = new URL(path, origin)
  url.searchParams.set('playback_format', 'mp4')
  url.searchParams.set('yf_ticket', 'v1')
  url.searchParams.set('yf_service_id', 'iot-system')
  url.searchParams.set('yf_user_id', '781')
  url.searchParams.set('yf_tenant_id', '1')
  url.searchParams.set('yf_camera_id', cameraId)
  url.searchParams.set('yf_action', 'playback')
  url.searchParams.set('yf_timestamp', '1783900000')
  url.searchParams.set('yf_nonce', 'nonce-01')
  url.searchParams.set('yf_signature', `sha256=${'a'.repeat(64)}`)
  return url.toString()
}

function playbackModalCalls() {
  const calls = []
  return {
    calls,
    modal: {
      openModal(...args) {
        calls.push(args)
      },
    },
  }
}

globalThis.__alertRecordQueryCalls = 0
const trustedPlaybackUrl = signedPlaybackUrl(
  'https://app.example.test',
  '/video/alert/record?path=record.mp4',
)
const trustedModal = playbackModalCalls()
assert.equal(await playAlertRecordInModal(trustedModal.modal, {
  device_id: 'camera-01',
  time: '2026-07-13T10:00:00+08:00',
  record_path: trustedPlaybackUrl,
}), true)
assert.equal(globalThis.__alertRecordQueryCalls, 0, 'trusted audited playback URL must not query VIDEO again')
assert.equal(trustedModal.calls.at(-1)?.[1]?.http_stream, trustedPlaybackUrl)

globalThis.__alertRecordQueryCalls = 0
const untrustedModal = playbackModalCalls()
assert.equal(await playAlertRecordInModal(untrustedModal.modal, {
  device_id: 'camera-01',
  time: '2026-07-13T10:00:00+08:00',
  record_path: signedPlaybackUrl('https://evil.example.test', '/video/alert/record'),
}), false)
assert.equal(globalThis.__alertRecordQueryCalls, 0, 'rejected audited playback URL must fail closed')
assert.equal(untrustedModal.calls.length, 0)

globalThis.__alertRecordQueryCalls = 0
const configuredOriginUrl = signedPlaybackUrl(
  'https://media.example.test',
  '/yfeieye/dev-api/video/record/camera-01/clip.mp4',
)
const configuredOriginModal = playbackModalCalls()
assert.equal(await playAlertRecordInModal(configuredOriginModal.modal, {
  device_id: 'camera-01',
  time: '2026-07-13T10:00:00+08:00',
  record_path: configuredOriginUrl,
}), true)
assert.equal(globalThis.__alertRecordQueryCalls, 0)
assert.equal(configuredOriginModal.calls.at(-1)?.[1]?.http_stream, configuredOriginUrl)

for (const [label, rejectedUrl] of [
  [
    'path outside VIDEO record endpoints',
    signedPlaybackUrl('https://app.example.test', '/system/users'),
  ],
  [
    'camera-scoped ticket for a different camera',
    signedPlaybackUrl('https://app.example.test', '/video/alert/record', 'camera-02'),
  ],
]) {
  globalThis.__alertRecordQueryCalls = 0
  const rejectedModal = playbackModalCalls()
  assert.equal(await playAlertRecordInModal(rejectedModal.modal, {
    device_id: 'camera-01',
    time: '2026-07-13T10:00:00+08:00',
    record_path: rejectedUrl,
  }), false, label)
  assert.equal(globalThis.__alertRecordQueryCalls, 0, `${label} must not bypass audit via VIDEO query`)
  assert.equal(rejectedModal.calls.length, 0)
}

console.log('alert review playback contract tests OK')
