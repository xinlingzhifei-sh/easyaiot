export const DEFAULT_TENANT_ID = 1

export function resolveTenantIdHeader(
  tenantEnable: string | undefined,
  tenantId: string | number | null | undefined,
): string | number | undefined {
  if (tenantEnable !== 'true')
    return undefined

  if (tenantId !== undefined && tenantId !== null && tenantId !== '')
    return tenantId

  return DEFAULT_TENANT_ID
}
