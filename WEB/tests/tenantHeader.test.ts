import * as assert from 'node:assert/strict'
import { resolveTenantIdHeader } from '../src/utils/http/axios/tenantHeader'

assert.equal(
  resolveTenantIdHeader('true', 7),
  7,
  'Tenant-enabled requests should prefer the cached tenant id.',
)

assert.equal(
  resolveTenantIdHeader('true', undefined),
  1,
  'Tenant-enabled requests should fall back to the default tenant id when an old session has no cached tenant id.',
)

assert.equal(
  resolveTenantIdHeader('false', undefined),
  undefined,
  'Tenant-disabled requests should not send a tenant-id header.',
)
