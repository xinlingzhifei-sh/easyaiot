import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const groupSource = readFileSync(
  fileURLToPath(new URL('../src/views/camera/utils/gb28181DeviceGroup.ts', import.meta.url)),
  'utf8',
)
const cardListSource = readFileSync(
  fileURLToPath(new URL('../src/views/camera/components/DeviceMixedCardList/index.vue', import.meta.url)),
  'utf8',
)

assert.match(
  groupSource,
  /Promise\.allSettled/,
  'Merged table device list should keep GB28181 rows even when the direct device API fails.',
)

assert.match(
  cardListSource,
  /Promise\.allSettled/,
  'Merged card device list should keep GB28181 rows even when the direct device API fails.',
)
