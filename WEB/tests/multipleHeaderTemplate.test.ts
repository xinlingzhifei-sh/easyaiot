import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const multipleHeader = readFileSync(resolve('src/layouts/default/header/MultipleHeader.vue'), 'utf8')

assert.match(
  multipleHeader,
  /v-if="!getFullContent && getIsShowPlaceholderDom"/,
  'Full-content pages should not render the multiple-header placeholder.',
)

assert.match(
  multipleHeader,
  /v-if="!getFullContent"/,
  'Full-content pages should not render the fixed multiple-header wrapper.',
)
