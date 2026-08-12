import type { RouteLocationRaw, Router } from 'vue-router'

export const ADMIN_ENTRY_ROUTE_NAME = 'ComputeNodeIndex'
export const ADMIN_ENTRY_CLUSTER_PATH = '/node/index'

export const ADMIN_ENTRY_FALLBACK_ROUTE: RouteLocationRaw = {
  path: ADMIN_ENTRY_CLUSTER_PATH,
}

export function resolveAdminEntryTarget(router: Pick<Router, 'hasRoute'>): RouteLocationRaw {
  if (router.hasRoute(ADMIN_ENTRY_ROUTE_NAME))
    return { name: ADMIN_ENTRY_ROUTE_NAME }

  return ADMIN_ENTRY_FALLBACK_ROUTE
}
