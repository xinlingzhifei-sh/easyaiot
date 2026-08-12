import * as assert from 'node:assert/strict'
import { resolveFullContent } from '../src/hooks/web/fullContent'

assert.equal(
  resolveFullContent({ query: { __full__: '' }, meta: {} }, false),
  true,
  'The __full__ query flag should keep forcing full-content mode.',
)

assert.equal(
  resolveFullContent({ query: { __full__: 'false' }, meta: { fullContent: true } }, false),
  false,
  'The __full__=false query flag should allow leaving full-content mode from a full-content route.',
)

assert.equal(
  resolveFullContent({ query: {}, meta: { fullContent: true } }, false),
  true,
  'Routes should be able to opt into full-content mode through route meta.',
)

assert.equal(
  resolveFullContent({ query: {}, meta: { fullContent: false } }, true),
  true,
  'Route meta fullContent=false should not override the global full-content setting.',
)
