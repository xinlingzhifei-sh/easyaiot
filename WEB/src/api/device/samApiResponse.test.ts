import assert from 'node:assert/strict'
import test from 'node:test'

import { unwrapSamApiPayload } from './samApiResponse'

test('SAM API response unwraps successful predict envelopes', () => {
  const payload = unwrapSamApiPayload<{ predictions: unknown[] }>({
    data: {
      code: 0,
      msg: 'success',
      data: {
        predictions: [],
      },
    },
  })

  assert.deepEqual(payload, { predictions: [] })
})

test('SAM API response throws backend message instead of generic 503 text', () => {
  assert.throws(
    () => unwrapSamApiPayload({
      data: {
        code: 503,
        msg: 'SAM 未启用，请设置 SAM_ENABLED=true',
      },
    }),
    /SAM 未启用/,
  )
})
