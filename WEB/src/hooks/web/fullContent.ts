import type { RouteLocationNormalizedLoaded } from 'vue-router'

type FullContentRoute = Pick<RouteLocationNormalizedLoaded, 'query' | 'meta'>

export function resolveFullContent(route: FullContentRoute, globalFullContent: boolean): boolean {
  if (route.query && Reflect.has(route.query, '__full__')) {
    const value = route.query.__full__
    const normalizedValue = Array.isArray(value) ? value[0] : value
    if (normalizedValue === 'false' || normalizedValue === '0')
      return false

    return true
  }

  return Boolean(route.meta?.fullContent) || globalFullContent
}
