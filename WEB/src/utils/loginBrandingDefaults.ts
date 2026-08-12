export interface LoginBrandingValues {
  loginName: string
  loginBgLight: string
  loginBgDark: string
}

export interface LoginBrandingResolutionOptions {
  current: LoginBrandingValues
  legacy: LoginBrandingValues
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

function resolveValue(value: unknown, currentDefault: string, legacyDefault: string): string {
  if (typeof value !== 'string' || !value.trim() || value === legacyDefault) {
    return currentDefault
  }
  return value
}
