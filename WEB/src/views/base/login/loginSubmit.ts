export type LoginSubmitResult = 'invalid' | 'captcha' | 'login'

const DEFAULT_TENANT_ID = 1

interface ResolveLoginTenantIdOptions {
  tenantEnable: string
  tenantName: string
  website: string
  getTenantByWebsite?: (website: string) => Promise<{ id?: number | string | null; name?: string | null } | null>
  getTenantIdByName: (name: string) => Promise<{ id?: number | string | null } | number | string | null>
  setTenantId: (tenantId: number | string) => void
}

interface RunLoginSubmitFlowOptions {
  captchaEnable: string
  validateForm: () => Promise<unknown>
  login: () => Promise<void>
  showCaptcha: () => void
}

export async function runLoginSubmitFlow({
  captchaEnable,
  validateForm,
  login,
  showCaptcha,
}: RunLoginSubmitFlowOptions): Promise<LoginSubmitResult> {
  try {
    const data = await validateForm()
    if (!data)
      return 'invalid'
  }
  catch {
    return 'invalid'
  }

  if (captchaEnable === 'false') {
    await login()
    return 'login'
  }

  showCaptcha()
  return 'captcha'
}

export async function resolveLoginTenantId({
  tenantEnable,
  tenantName,
  website,
  getTenantByWebsite,
  getTenantIdByName,
  setTenantId,
}: ResolveLoginTenantIdOptions): Promise<number | string | undefined> {
  if (tenantEnable !== 'true')
    return undefined

  if (getTenantByWebsite && website) {
    try {
      const tenant = await getTenantByWebsite(website)
      if (tenant?.id !== undefined && tenant.id !== null && tenant.id !== '') {
        setTenantId(tenant.id)
        return tenant.id
      }
    }
    catch {
      // Domain tenant lookup is a convenience probe. Keep login usable when it fails.
    }
  }

  try {
    const tenant = await getTenantIdByName(tenantName)
    const tenantId = typeof tenant === 'object' && tenant !== null ? tenant.id : tenant
    if (tenantId !== undefined && tenantId !== null && tenantId !== '') {
      setTenantId(tenantId)
      return tenantId
    }
  }
  catch {
    // Fall through to the built-in default tenant used by request headers.
  }

  setTenantId(DEFAULT_TENANT_ID)
  return DEFAULT_TENANT_ID
}

export function extractLoginErrorMessage(error: unknown, fallbackMessage: string): string {
  if (!error || typeof error !== 'object')
    return fallbackMessage

  const record = error as Record<string, any>
  return record.response?.data?.message
    || record.data?.message
    || record.message
    || fallbackMessage
}
