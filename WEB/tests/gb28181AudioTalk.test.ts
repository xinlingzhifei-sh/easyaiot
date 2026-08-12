import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolveGbAudioBroadcastStreamInfo } from '../src/components/VideoPlayer/monitor/gb28181AudioTalkResponse'

const audioTalk = readFileSync(
  fileURLToPath(
    new URL('../src/components/VideoPlayer/monitor/useGb28181AudioTalk.ts', import.meta.url),
  ),
  'utf8',
)
const gb28181Api = readFileSync(
  fileURLToPath(new URL('../src/api/device/gb28181.ts', import.meta.url)),
  'utf8',
)

assert.throws(
  () => resolveGbAudioBroadcastStreamInfo({
    data: {
      code: 0,
      data: {
        code: 400,
        msg: '该通道未启用语音对讲',
      },
    },
  }),
  /该通道未启用语音对讲/,
  'Nested GB28181 business errors must keep the backend reason instead of becoming a missing WebRTC URL error.',
)

assert.deepEqual(
  resolveGbAudioBroadcastStreamInfo({
    data: {
      code: 0,
      data: {
        streamInfo: {
          rtcs: 'https://example.test/index/api/webrtc?type=push',
        },
      },
    },
  }),
  {
    rtcs: 'https://example.test/index/api/webrtc?type=push',
  },
  'A successful nested GB28181 response must still expose the WebRTC push stream.',
)

assert.doesNotMatch(
  audioTalk,
  /navigator\.mediaDevices\.getUserMedia/,
  'The ZLM endpoint must be the single owner of microphone capture.',
)
assert.doesNotMatch(
  audioTalk,
  /pushClient\.start\(/,
  'The ZLM endpoint starts itself in its constructor and must not be started twice.',
)
assert.match(
  audioTalk,
  /WEBRTC_ON_LOCAL_STREAM[\s\S]*localStream\s*=\s*stream/,
  'The microphone stream emitted by ZLM must be retained for level display and cleanup.',
)
assert.match(
  audioTalk,
  /CAPTURE_STREAM_FAILED[\s\S]*WEBRTC_OFFER_ANWSER_EXCHANGE_FAILED[\s\S]*WEBRTC_ICE_CANDIDATE_ERROR/,
  'Microphone, SDP exchange and ICE failures must end the connecting state.',
)
assert.match(
  audioTalk,
  /GB28181_TALK_CONNECTION_TIMEOUT_MS[\s\S]*setTimeout/,
  'GB28181 talk setup must have a bounded connection timeout.',
)
assert.match(
  gb28181Api,
  /GB28181_TALK_REQUEST_TIMEOUT_MS[\s\S]*startGbAudioBroadcast[\s\S]*timeout:\s*GB28181_TALK_REQUEST_TIMEOUT_MS/,
  'The broadcast address request must have a bounded HTTP timeout.',
)
