import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import {
  filterValidWvpDevices,
  isValidGb28181SipDeviceId,
  resolveWvpSipDeviceId,
} from '../src/views/camera/utils/gb28181DeviceId'

assert.equal(isValidGb28181SipDeviceId('44010200493432381460'), true)
assert.equal(isValidGb28181SipDeviceId('me'), false)
assert.equal(isValidGb28181SipDeviceId('.'), false)
assert.equal(isValidGb28181SipDeviceId('1234567890'), false)
assert.equal(isValidGb28181SipDeviceId(''), false)

assert.equal(
  resolveWvpSipDeviceId({ deviceIdentification: '44010200493432381460' }),
  '44010200493432381460',
)
assert.equal(resolveWvpSipDeviceId({ deviceId: 'me' }), 'me')

const devices = filterValidWvpDevices([
  { deviceId: 'me', on_line: false },
  { deviceId: '.', on_line: false },
  { deviceId: '44010200493432381460', on_line: true },
])

assert.deepEqual(devices.map((device) => device.deviceId), ['44010200493432381460'])

const gb28181ApiSource = readFileSync(
  fileURLToPath(new URL('../src/api/device/gb28181.ts', import.meta.url)),
  'utf8',
)
const deviceGroupSource = readFileSync(
  fileURLToPath(new URL('../src/views/camera/utils/gb28181DeviceGroup.ts', import.meta.url)),
  'utf8',
)

assert.match(
  gb28181ApiSource,
  /filterValidWvpDevices\(rawList\)/,
  'The GB28181 API normalizer should hide malformed WVP SIP devices before they reach device lists.',
)
assert.match(
  gb28181ApiSource,
  /rawCount/,
  'queryAllVideoList should keep paginating by the raw WVP page size after filtering invalid devices.',
)
assert.match(
  deviceGroupSource,
  /filterValidWvpDevices\(gbRes\?\.data \?\? \[\]\)/,
  'The merged camera table should filter malformed WVP SIP devices.',
)
assert.match(
  deviceGroupSource,
  /filterValidWvpDevices\(wvpDevices\)/,
  'The merged camera cards should filter malformed WVP SIP devices.',
)
