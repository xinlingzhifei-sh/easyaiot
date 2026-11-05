export interface LoginBrandingValues {
  loginName: string
  loginBgLight: string
  loginBgDark: string
}

/** 历史默认值：可为单个值或多个值（多次改名后，各代默认值都应回落到当前默认值） */
export type LegacyLoginBrandingValues = {
  [K in keyof LoginBrandingValues]: string | readonly string[]
}

export interface LoginBrandingResolutionOptions {
  current: LoginBrandingValues
  legacy: LegacyLoginBrandingValues
}

export function resolveLoginBrandingValues(
  stored: Partial<Record<keyof LoginBrandingValues, unknown>>,
  options: LoginBrandingResolutionOptions,
): LoginBrandingValues {
  return {
    loginName: resolveValue(
      stored.loginName,
      options.current.loginName,
      options.legacy.loginName,
    ),
    loginBgLight: resolveValue(
      stored.loginBgLight,
      options.current.loginBgLight,
      options.legacy.loginBgLight,
    ),
    loginBgDark: resolveValue(
      stored.loginBgDark,
      options.current.loginBgDark,
      options.legacy.loginBgDark,
    ),
  }
}

function resolveValue(value: unknown, currentDefault: string, legacyDefault: string | readonly string[]): string {
  const legacyDefaults: readonly string[] = typeof legacyDefault === 'string' ? [legacyDefault] : legacyDefault
  if (typeof value !== 'string' || !value.trim() || legacyDefaults.includes(value)) {
    return currentDefault
  }
  return value
}
