type JsonRecord = Record<string, unknown>

function asRecord(value: unknown): JsonRecord | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as JsonRecord
    : null
}

function isBusinessFailure(value: unknown): boolean {
  if (typeof value !== 'number' && typeof value !== 'string') return false
  if (typeof value === 'string' && !value.trim()) return false
  const code = Number(value)
  return Number.isFinite(code) && code !== 0 && code !== 200
}

export function resolveGbAudioBroadcastStreamInfo(response: unknown): JsonRecord | null {
  const responseRecord = asRecord(response)
  const responseBody = asRecord(responseRecord?.data) ?? responseRecord
  const payload = asRecord(responseBody?.data) ?? responseBody
  const failure = [responseBody, payload].find((item) => isBusinessFailure(item?.code))

  if (failure) {
    const message = typeof failure.msg === 'string' ? failure.msg.trim() : ''
    throw new Error(message || `启动国标对讲失败（业务码 ${String(failure.code)}）`)
  }

  return asRecord(payload?.streamInfo) ?? asRecord(responseBody?.streamInfo)
}
