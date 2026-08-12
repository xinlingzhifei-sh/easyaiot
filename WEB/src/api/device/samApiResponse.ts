const SAM_API_SUCCESS_CODES = new Set([0, 200])

export function unwrapSamApiPayload<T>(responseOrBody: unknown, fallback = '请求失败'): T {
  const body = ((responseOrBody as { data?: unknown })?.data ?? responseOrBody) as {
    code?: number
    msg?: string
    data?: T
  }

  if (typeof body?.code === 'number') {
    if (!SAM_API_SUCCESS_CODES.has(body.code))
      throw new Error(body.msg || fallback)
    return (body.data ?? {}) as T
  }

  return body as T
}
