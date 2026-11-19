import assert from 'node:assert/strict'
import { alertTimeMillis } from '../src/views/alert/alertDisplay'

const epoch = Date.UTC(2026, 10, 19, 13, 35, 10)
for (const input of ['2026-11-19 21:35:10', '2026-11-19T13:35:10Z', '2026-11-19T21:35:10+08:00', epoch, String(epoch)]) {
  assert.equal(alertTimeMillis(input), epoch)
}
assert.equal(alertTimeMillis('2026-11-19 21:35:10.125'), epoch + 125)
for (const input of [undefined, null, '', '2026-11-19', 'invalid', NaN]) {
  assert.equal(alertTimeMillis(input), undefined)
}
