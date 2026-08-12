export interface LocalBackRoute {
  path?: string
  name?: string | symbol
  meta?: {
    title?: unknown
  }
  children?: LocalBackRoute[]
}

function normalizedTitle(route: LocalBackRoute): string {
  return String(route.meta?.title ?? '').trim()
}

function normalizedIdentityText(value: unknown): string {
  return String(value ?? '').trim().toLowerCase()
}

function routeIdentityTexts(route: LocalBackRoute): string[] {
  return [
    normalizedIdentityText(route.meta?.title),
    normalizedIdentityText(route.name),
    normalizedIdentityText(route.path),
  ].filter(Boolean)
}

function isClusterManagementRoute(route: LocalBackRoute): boolean {
  const texts = routeIdentityTexts(route)
  return texts.some((text) => {
    if (text === '集群管理' || text === 'nodemanage') return true
    if (text.endsWith('/node') || text.includes('/node/')) return true
    return text.includes('集群管理')
  })
}

function hasEquivalentRoute(routes: LocalBackRoute[], localRoute: LocalBackRoute): boolean {
  const localTitle = normalizedTitle(localRoute)
  return routes.some((route) => {
    if (route.name && localRoute.name && route.name === localRoute.name) return true
    if (route.path && localRoute.path && route.path === localRoute.path) return true
    if (localTitle && normalizedTitle(route) === localTitle) return true
    if (isClusterManagementRoute(localRoute) && isClusterManagementRoute(route)) return true
    return hasEquivalentRoute(route.children ?? [], localRoute)
  })
}

export function resolveRequiredLocalBackRoutes<T extends LocalBackRoute>(
  backendRoutes: LocalBackRoute[],
  localRoutes: T[],
): T[] {
  return localRoutes.filter((route) => !hasEquivalentRoute(backendRoutes, route))
}
