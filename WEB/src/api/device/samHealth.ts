export interface SamHealth {
  status: string
  engine?: string
  model_loaded?: boolean
  device?: string
  enabled?: boolean
  message?: string
}

function isSamHealth(value: unknown): value is SamHealth {
  return !!value && typeof value === 'object' && typeof (value as { status?: unknown }).status === 'string'
}

export function unwrapSamHealthPayload(responseOrBody: unknown): SamHealth {
  const body = ((responseOrBody as { data?: unknown })?.data ?? responseOrBody) as unknown
  const envelopeData = (body as { data?: unknown })?.data

  if (isSamHealth(envelopeData))
    return envelopeData
  if (isSamHealth(body))
    return body
  return { status: 'unknown' }
}

export function getSamHealthPayloadFromError(error: unknown): SamHealth | null {
  const body = (error as { response?: { data?: unknown } })?.response?.data

  if (!body)
    return null
  return unwrapSamHealthPayload(body)
}

export function canRunSamPredict(health: SamHealth | null | undefined): boolean {
  if (!health)
    return true
  return health.enabled !== false && health.status !== 'disabled' && health.status !== 'unhealthy'
}

export function getSamUnavailableMessage(health: SamHealth | null | undefined): string | null {
  if (!health)
    return null
  if (health.enabled === false || health.status === 'disabled')
    return 'SAM 服务未启用，请先完成模型配置并设置 SAM_ENABLED=true'
  if (health.status === 'unhealthy')
    return 'SAM 模型未加载，请检查模型权重和服务配置'
  return null
}
