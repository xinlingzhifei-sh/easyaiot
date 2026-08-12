const LOCAL_STREAM_HOSTS = new Set(['localhost', '127.0.0.1', '0.0.0.0', '::1'])

function getBrowserLocation(): Pick<Location, 'protocol' | 'host' | 'hostname'> | null {
  if (typeof window === 'undefined' || !window.location) return null
  return window.location
}

export function isLocalOrPrivateStreamHost(hostname: string): boolean {
  const host = hostname.trim().toLowerCase()
  if (!host) return false
  if (LOCAL_STREAM_HOSTS.has(host)) return true
  if (/^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host)) return true
  if (/^192\.168\.\d{1,3}\.\d{1,3}$/.test(host)) return true
  if (/^169\.254\.\d{1,3}\.\d{1,3}$/.test(host)) return true

  const private172 = /^172\.(\d{1,2})\.\d{1,3}\.\d{1,3}$/.exec(host)
  if (private172) {
    const secondOctet = Number(private172[1])
    return secondOctet >= 16 && secondOctet <= 31
  }

  return false
}

export function rewriteStreamHostToPageHost(url: string): string {
  const trimmed = url?.trim()
  const pageLocation = getBrowserLocation()
  if (!trimmed || !pageLocation) return trimmed

  try {
    const parsed = new URL(trimmed)
    const pageHost = pageLocation.host
    const pageHostname = pageLocation.hostname
    if (!pageHost || !pageHostname || isLocalOrPrivateStreamHost(pageHostname)) {
      return trimmed
    }

    const streamHostname = parsed.hostname
    const shouldRewrite =
      streamHostname === pageHostname || isLocalOrPrivateStreamHost(streamHostname)

    if (!shouldRewrite) return trimmed

    const publicOrigin = new URL(`${pageLocation.protocol || parsed.protocol}//${pageHost}`)
    parsed.protocol = publicOrigin.protocol
    parsed.hostname = publicOrigin.hostname
    parsed.port = publicOrigin.port
    return parsed.toString()
  } catch {
    return trimmed
  }
}

export function rewriteStreamUrlForBrowser(url: string): string {
  return rewriteStreamHostToPageHost(url)
}

export function convertRtmpToHttp(rtmpUrl: string): string | null {
  const trimmed = rtmpUrl?.trim()
  if (!trimmed || !trimmed.startsWith('rtmp://')) {
    return null
  }

  try {
    const url = new URL(trimmed)
    const server = url.hostname
    let path = url.pathname.replace(/^\//, '')
    if (!path) path = 'live'
    if (!path.endsWith('.flv')) path = `${path}.flv`
    return rewriteStreamHostToPageHost(`http://${server}:8080/${path}`)
  } catch {
    return null
  }
}
