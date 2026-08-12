import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const read = (path: string) => readFileSync(resolve(repoRoot, path), 'utf8')

const compose = read('.scripts/docker/docker-compose.yml')
assert.match(
  compose,
  /NodeRED:[\s\S]*?ports:\s*\n\s*- "127\.0\.0\.1:1880:1880"/,
  'NodeRED must not publish its unauthenticated editor port on every host interface.',
)

const auth = read('WEB/src/utils/auth/index.ts')
assert.match(auth, /const NODE_RED_AUTH_COOKIE = 'yfeieye_node_red_token'/)
assert.match(
  auth,
  /const NODE_RED_AUTH_PATHS = \['\/dev-api\/nodeRed', '\/yfeieye\/dev-api\/nodeRed', '\/nodeRed'\]/,
)
assert.match(auth, /SameSite=Strict/)
assert.match(auth, /window\.location\.protocol === 'https:' \? '; Secure'/)
assert.match(auth, /export function getAccessToken[\s\S]*?syncNodeRedAuthCookie\(token\)/)
assert.match(auth, /export function setAccessToken[\s\S]*?syncNodeRedAuthCookie\(value\)/)
assert.match(auth, /export function clearAuthCache[\s\S]*?syncNodeRedAuthCookie\(null\)/)

function assertProtectedProxy(
  path: string,
  expectedLocations: number,
  expectedAuthBackends = 1,
  authBackend = 'gateway:48080',
) {
  const source = read(path)
  assert.match(source, /"Bearer missing-node-red-token"/)
  assert.match(source, /location = \/_node_red_auth \{[\s\S]*?internal;/)
  assert.ok(
    source.includes(
      `proxy_pass http://${authBackend}/admin-api/system/auth/get-permission-info;`,
    ),
    `${path} must route NodeRED auth checks through ${authBackend}.`,
  )
  assert.equal(
    source.match(/auth_request \/_node_red_auth;/g)?.length,
    expectedLocations,
    `${path} must protect every NodeRED proxy location.`,
  )
  assert.equal(
    source.match(/proxy_set_header Cookie "";/g)?.length,
    expectedLocations + expectedAuthBackends,
    `${path} must not forward the platform token cookie to NodeRED or the auth backend.`,
  )
}

assertProtectedProxy('WEB/conf/nginx.conf', 5, 2)
assertProtectedProxy('WEB/conf/nginx.mini.conf', 2, 2, 'system-host:48099')
