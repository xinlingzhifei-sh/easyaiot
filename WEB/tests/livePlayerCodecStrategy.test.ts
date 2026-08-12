import * as assert from 'node:assert/strict'

import {
  detectVideoCodecFromUrl,
  canPlayNativeHevc,
  getStreamVideoCodec,
  isFmp4StreamUrl,
  normalizeVideoCodec,
  pickLivePlayerEngine,
  pickWvpPlaySource,
  pickWvpPlaySources,
  shouldUseWasmLivePlayer,
} from '../src/views/camera/utils/livePlayer'

assert.equal(normalizeVideoCodec('H.265'), 'h265')
assert.equal(normalizeVideoCodec('HEVC'), 'h265')
assert.equal(normalizeVideoCodec('H264'), 'h264')
assert.equal(normalizeVideoCodec('AVC'), 'h264')
assert.equal(normalizeVideoCodec('MPEG-4'), 'mpeg4')
assert.equal(normalizeVideoCodec('MJPEG'), 'mjpeg')
assert.equal(normalizeVideoCodec('JPEG'), 'mjpeg')
assert.equal(normalizeVideoCodec(''), 'unknown')

assert.equal(
  detectVideoCodecFromUrl('https://eye.yfeiai.com/rtp/demo.live.flv?originTypeStr=rtp_push&videoCodec=H265'),
  'h265',
)
assert.equal(detectVideoCodecFromUrl('not a url'), 'unknown')
assert.equal(isFmp4StreamUrl('https://eye.yfeiai.com/rtp/demo.live.mp4?videoCodec=H265'), true)
assert.equal(isFmp4StreamUrl('https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265'), false)

assert.equal(
  getStreamVideoCodec({
    mediaInfo: { videoCodec: 'HEVC' },
    tracks: [{ codec_type: 0, codec_id_name: 'H264' }],
  }),
  'h265',
)
assert.equal(
  getStreamVideoCodec({
    tracks: [{ codec_type: 0, codec_id_name: 'H264' }],
  }),
  'h264',
)

assert.equal(pickLivePlayerEngine({ videoCodec: 'H265' }), 'easywasm')
assert.equal(pickLivePlayerEngine({ videoCodec: 'H264' }), 'jessibuca')
assert.equal(pickLivePlayerEngine({ videoCodec: 'MPEG4' }), 'easywasm')
assert.equal(pickLivePlayerEngine({ videoCodec: 'MJPEG' }), 'easywasm')
assert.equal(
  pickLivePlayerEngine({
    url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=HEVC',
  }),
  'easywasm',
)
assert.equal(pickLivePlayerEngine({ videoCodec: 'VP9' }), 'jessibuca')
assert.equal(
  shouldUseWasmLivePlayer({
    playerEngine: 'easywasm',
    videoCodec: 'H264',
    url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
  }),
  false,
)
assert.equal(
  shouldUseWasmLivePlayer({
    playerEngine: 'easywasm',
    videoCodec: 'H265',
    url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  }),
  true,
)

const source = pickWvpPlaySource({
  flv: 'http://eye.yfeiai.com:6080/rtp/demo.live.flv?videoCodec=H265',
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  ws_flv: 'ws://eye.yfeiai.com:6080/rtp/demo.live.flv?videoCodec=H265',
  wss_flv: '',
  mediaInfo: { videoCodec: 'H265' },
}, {
  isHttps: true,
})

assert.deepEqual(source, {
  url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  videoCodec: 'h265',
  playerEngine: 'easywasm',
})

const h264StableSources = pickWvpPlaySources({
  rtcs: 'https://eye.yfeiai.com/index/api/webrtc?app=rtp&stream=demo&type=play&videoCodec=H264',
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
  wss_flv: 'wss://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
  mediaInfo: { videoCodec: 'H264' },
}, {
  isHttps: true,
})

assert.deepEqual(h264StableSources, [
  {
    label: 'https_flv',
    url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
    videoCodec: 'h264',
    playerEngine: 'jessibuca',
  },
  {
    label: 'wss_flv',
    url: 'wss://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
    videoCodec: 'h264',
    playerEngine: 'jessibuca',
  },
  {
    label: 'rtcs',
    url: 'https://eye.yfeiai.com/index/api/webrtc?app=rtp&stream=demo&type=play&videoCodec=H264',
    videoCodec: 'h264',
    playerEngine: 'webrtc',
  },
])

assert.deepEqual(pickWvpPlaySource({
  rtcs: 'https://eye.yfeiai.com/index/api/webrtc?app=rtp&stream=demo&type=play&videoCodec=H264',
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
  mediaInfo: { videoCodec: 'H264' },
}, {
  isHttps: true,
}), {
  url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H264',
  videoCodec: 'h264',
  playerEngine: 'jessibuca',
})

assert.deepEqual(pickWvpPlaySource({
  rtcs: 'https://eye.yfeiai.com/index/api/webrtc?app=rtp&stream=demo&type=play&videoCodec=H265',
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  mediaInfo: { videoCodec: 'H265' },
}, {
  isHttps: true,
}), {
  url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  videoCodec: 'h265',
  playerEngine: 'easywasm',
})

assert.deepEqual(pickWvpPlaySource({
  rtcs: 'https://eye.yfeiai.com/index/api/webrtc?app=rtp&stream=demo&type=play',
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv',
}, {
  isHttps: true,
}), {
  url: 'https://eye.yfeiai.com/rtp/demo.live.flv',
  videoCodec: 'unknown',
  playerEngine: 'jessibuca',
})

const publicH265Source = pickWvpPlaySource({
  https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  wss_flv: 'wss://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  mediaInfo: { videoCodec: 'H265' },
}, {
  isHttps: true,
})

assert.deepEqual(publicH265Source, {
  url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
  videoCodec: 'h265',
  playerEngine: 'easywasm',
})

const originalDocument = globalThis.document

Object.defineProperty(globalThis, 'document', {
  configurable: true,
  value: {
    createElement(tagName: string) {
      assert.equal(tagName, 'video')
      return {
        canPlayType(mimeType: string) {
          return mimeType.includes('hev1') || mimeType.includes('hvc1') ? 'probably' : ''
        },
      }
    },
  },
})

try {
  assert.equal(canPlayNativeHevc(), true)
  assert.equal(
    pickLivePlayerEngine({
      videoCodec: 'H265',
      url: 'https://eye.yfeiai.com/rtp/demo.live.mp4?videoCodec=H265',
    }),
    'easywasm',
  )

  const browserReportedHevcSource = pickWvpPlaySource({
    https_flv: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
    https_fmp4: 'https://eye.yfeiai.com/rtp/demo.live.mp4?videoCodec=H265',
    wss_flv: 'wss://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
    mediaInfo: { videoCodec: 'H265' },
  }, {
    isHttps: true,
  })

  assert.deepEqual(browserReportedHevcSource, {
    url: 'https://eye.yfeiai.com/rtp/demo.live.flv?videoCodec=H265',
    videoCodec: 'h265',
    playerEngine: 'easywasm',
  })
} finally {
  if (originalDocument === undefined) {
    delete (globalThis as typeof globalThis & { document?: Document }).document
  } else {
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      value: originalDocument,
    })
  }
}
