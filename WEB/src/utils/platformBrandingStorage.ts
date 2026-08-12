import defaultLogo from '@/assets/images/logo.png'
import legacyDefaultDarkBg from '@/assets/images/dark-bg.png'
import legacyDefaultLightBg from '@/assets/images/light-bg.png'
import defaultLoginBg from '@/assets/svg/login-yfeiEye.svg'
import { resolveLoginBrandingValues } from '@/utils/loginBrandingDefaults'

const PLATFORM_BRANDING_STORAGE_KEY = 'PLATFORM_BRANDING_CONFIG'
export const PLATFORM_BRANDING_FAB_HIDDEN_KEY = 'PLATFORM_BRANDING_FAB_HIDDEN'
export const DEFAULT_LOGIN_NAME = '逸飞 AI 智眼管控平台'
export const DEFAULT_DASHBOARD_TITLE = '逸飞AI智眼监控平台'
const LEGACY_DEFAULT_LOGIN_NAME = '逸飞AI智眼系统'
const LEGACY_DEFAULT_DASHBOARD_TITLE = '云边端一体' + '算法预警监控平台'

export interface PlatformBrandingConfig {
  /** 管理后台平台名称（侧边栏、浏览器标题等） */
  platformName: string
  /** 管理后台平台 Logo */
  platformLogo: string
  /** 管理后台平台 Logo 文件编号，空值表示使用内置默认图片 */
  platformLogoFileId: number | null
  /** 大屏顶部标题 */
  dashboardTitle: string
  /** 登录页左侧名称 */
  loginName: string
  /** 登录页 Logo */
  loginLogo: string
  /** 登录页 Logo 文件编号，空值表示使用内置默认图片 */
  loginLogoFileId: number | null
  /** 登录表单标题，留空则使用 i18n 默认文案 */
  loginFormTitle: string
  /** 登录页浅色背景 */
  loginBgLight: string
  /** 登录页浅色背景文件编号，空值表示使用内置默认图片 */
  loginBgLightFileId: number | null
  /** 登录页深色背景 */
  loginBgDark: string
  /** 登录页深色背景文件编号，空值表示使用内置默认图片 */
  loginBgDarkFileId: number | null
}

export function getDefaultPlatformBranding(): PlatformBrandingConfig {
  const envTitle = import.meta.env.VITE_GLOB_APP_TITLE || '云边端一体化智能算法应用平台'
  return {
    platformName: envTitle,
    platformLogo: defaultLogo,
    platformLogoFileId: null,
    dashboardTitle: DEFAULT_DASHBOARD_TITLE,
    loginName: DEFAULT_LOGIN_NAME,
    loginLogo: defaultLogo,
    loginLogoFileId: null,
    loginFormTitle: '',
    loginBgLight: defaultLoginBg,
    loginBgLightFileId: null,
    loginBgDark: defaultLoginBg,
    loginBgDarkFileId: null,
  }
}

export function loadPlatformBrandingConfig(): PlatformBrandingConfig {
  const defaults = getDefaultPlatformBranding()
  const raw = readJson(PLATFORM_BRANDING_STORAGE_KEY)
  if (!raw || typeof raw !== 'object') {
    return { ...defaults }
  }
  const data = raw as Partial<PlatformBrandingConfig>
  const loginBranding = resolveLoginBrandingValues(data, {
    current: {
      loginName: defaults.loginName,
      loginBgLight: defaults.loginBgLight,
      loginBgDark: defaults.loginBgDark,
    },
    legacy: {
      loginName: LEGACY_DEFAULT_LOGIN_NAME,
      loginBgLight: legacyDefaultLightBg,
      loginBgDark: legacyDefaultDarkBg,
    },
  })
  return {
    platformName: pickString(data.platformName, defaults.platformName),
    platformLogo: pickString(data.platformLogo, defaults.platformLogo),
    platformLogoFileId: data.platformLogoFileId ?? null,
    dashboardTitle: resolveDashboardTitle(data.dashboardTitle, defaults.dashboardTitle),
    loginName: loginBranding.loginName,
    loginLogo: pickString(data.loginLogo, defaults.loginLogo),
    loginLogoFileId: data.loginLogoFileId ?? null,
    loginFormTitle: pickString(data.loginFormTitle, defaults.loginFormTitle),
    loginBgLight: loginBranding.loginBgLight,
    loginBgLightFileId: data.loginBgLightFileId ?? null,
    loginBgDark: loginBranding.loginBgDark,
    loginBgDarkFileId: data.loginBgDarkFileId ?? null,
  }
}

/** @returns 是否写入成功（空间不足等场景会返回 false） */
export function savePlatformBrandingConfig(config: PlatformBrandingConfig): boolean {
  return writeJson(PLATFORM_BRANDING_STORAGE_KEY, config)
}

/** 服务端配置加载成功后清除历史浏览器品牌数据，避免旧值再次成为配置来源 */
export function clearPlatformBrandingConfig(): void {
  try {
    window.localStorage.removeItem(PLATFORM_BRANDING_STORAGE_KEY)
  }
  catch (error) {
    console.error(error)
  }
}

export function loadFabHiddenState(): boolean {
  return readJson(PLATFORM_BRANDING_FAB_HIDDEN_KEY) === true
}

export function saveFabHiddenState(hidden: boolean): void {
  writeJson(PLATFORM_BRANDING_FAB_HIDDEN_KEY, hidden)
}

function pickString(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.trim() ? value : fallback
}

function resolveDashboardTitle(value: unknown, fallback: string): string {
  if (typeof value === 'string' && value.trim() === LEGACY_DEFAULT_DASHBOARD_TITLE)
    return fallback
  return pickString(value, fallback)
}
/** 使用原生 JSON，避免通用 storage 工具在退出登录时被一并清空后的二次解析问题 */
function readJson(key: string): unknown {
  try {
    const item = window.localStorage.getItem(key)
    if (!item)
      return null
    return JSON.parse(item)
  }
  catch (error) {
    console.error(error)
    return null
  }
}

function writeJson(key: string, value: unknown): boolean {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
    return true
  }
  catch (error) {
    console.error(error)
    return false
  }
}
