export type LivePlayerEngine = 'jessibuca' | 'easywasm' | 'native' | 'webrtc';
export type NormalizedVideoCodec =
  | 'h265'
  | 'h264'
  | 'mpeg4'
  | 'mjpeg'
  | 'unknown';

export interface WvpPlaySource {
  url: string;
  videoCodec: NormalizedVideoCodec;
  playerEngine: LivePlayerEngine;
}

export interface WvpPlaySourceOption extends WvpPlaySource {
  label: string;
}

const WASM_VIDEO_CODECS = new Set<NormalizedVideoCodec>(['h265', 'mpeg4', 'mjpeg']);
const NATIVE_HEVC_CODECS = [
  'video/mp4; codecs="hev1.1.6.L123.B0"',
  'video/mp4; codecs="hvc1.1.6.L123.B0"',
  'video/mp4; codecs="hev1"',
  'video/mp4; codecs="hvc1"',
];

export function normalizeVideoCodec(codec?: string | null): NormalizedVideoCodec {
  const normalized = String(codec ?? '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');

  if (!normalized) return 'unknown';
  if (normalized === 'h265' || normalized === 'hevc' || normalized === 'hvc1') return 'h265';
  if (normalized === 'h264' || normalized === 'avc' || normalized === 'avc1') return 'h264';
  if (normalized === 'mpeg4' || normalized === 'mp4v') return 'mpeg4';
  if (normalized === 'mjpeg' || normalized === 'jpeg' || normalized === 'jpg') return 'mjpeg';
  return 'unknown';
}

export function detectVideoCodecFromUrl(url?: string | null): NormalizedVideoCodec {
  const raw = url?.trim();
  if (!raw) return 'unknown';
  try {
    const parsed = new URL(raw);
    return normalizeVideoCodec(
      parsed.searchParams.get('videoCodec') ??
        parsed.searchParams.get('codec') ??
        parsed.searchParams.get('vcodec'),
    );
  } catch {
    return 'unknown';
  }
}

export function isFmp4StreamUrl(url?: string | null): boolean {
  const raw = url?.trim();
  if (!raw) return false;
  try {
    return new URL(raw).pathname.toLowerCase().endsWith('.mp4');
  } catch {
    return /\.mp4(?:\?|#|$)/i.test(raw);
  }
}

export function canPlayNativeHevc(): boolean {
  if (typeof document === 'undefined') return false;
  const video = document.createElement('video');
  return NATIVE_HEVC_CODECS.some((codec) => video.canPlayType(codec) !== '');
}

export function getStreamVideoCodec(streamContent?: Record<string, any> | null): NormalizedVideoCodec {
  if (!streamContent) return 'unknown';

  const directCodec = normalizeVideoCodec(
    streamContent.videoCodec ??
      streamContent.video_codec ??
      streamContent.mediaInfo?.videoCodec ??
      streamContent.mediaInfo?.video_codec,
  );
  if (directCodec !== 'unknown') return directCodec;

  const tracks = streamContent.tracks ?? streamContent.mediaInfo?.tracks;
  if (Array.isArray(tracks)) {
    const videoTrack = tracks.find((track) => Number(track?.codec_type) === 0);
    return normalizeVideoCodec(videoTrack?.codec_id_name ?? videoTrack?.codecName);
  }

  return 'unknown';
}

export function pickLivePlayerEngine(options: {
  videoCodec?: string | null;
  url?: string | null;
}): LivePlayerEngine {
  const codec = resolveLiveVideoCodec(options);
  return WASM_VIDEO_CODECS.has(codec) ? 'easywasm' : 'jessibuca';
}

export function shouldUseWasmLivePlayer(options: {
  playerEngine?: string | null;
  videoCodec?: string | null;
  url?: string | null;
}): boolean {
  const codec = resolveLiveVideoCodec(options);
  if (options.playerEngine === 'easywasm') return codec !== 'h264';
  if (options.playerEngine === 'jessibuca') return false;
  if (options.playerEngine === 'webrtc') return false;
  return WASM_VIDEO_CODECS.has(codec);
}

function resolveLiveVideoCodec(options: {
  videoCodec?: string | null;
  url?: string | null;
}): NormalizedVideoCodec {
  const codec = normalizeVideoCodec(options.videoCodec);
  return codec === 'unknown' ? detectVideoCodecFromUrl(options.url) : codec;
}

export function pickWvpPlaySources(
  streamContent: Record<string, any> | null | undefined,
  options?: {
    isHttps?: boolean;
    toBrowserPlayUrl?: (url?: string | null) => string | null;
  },
): WvpPlaySourceOption[] {
  if (!streamContent) return [];
  const isHttps =
    options?.isHttps ??
    (typeof window !== 'undefined' && window.location.protocol === 'https:');
  const toPlayableUrl =
    options?.toBrowserPlayUrl ??
    ((url?: string | null) => {
      const trimmed = url?.trim();
      return trimmed || null;
    });
  const videoCodec = getStreamVideoCodec(streamContent);
  const sources: WvpPlaySourceOption[] = [];
  const pushSource = (
    label: string,
    raw: string | null | undefined,
    playerEngine?: LivePlayerEngine,
  ) => {
    const url = toPlayableUrl(raw);
    if (url) {
      sources.push({
        label,
        url,
        videoCodec,
        playerEngine: playerEngine ?? pickLivePlayerEngine({ videoCodec, url }),
      });
    }
  };

  const candidates = isHttps
    ? [
        ['https_flv', streamContent.https_flv],
        ['wss_flv', streamContent.wss_flv],
        ['https_fmp4', streamContent.https_fmp4],
        ['wss_fmp4', streamContent.wss_fmp4],
        ['flv', streamContent.flv],
        ['ws_flv', streamContent.ws_flv],
        ['fmp4', streamContent.fmp4],
      ]
    : [
        ['ws_flv', streamContent.ws_flv],
        ['flv', streamContent.flv],
        ['ws_fmp4', streamContent.ws_fmp4],
        ['fmp4', streamContent.fmp4],
        ['https_flv', streamContent.https_flv],
        ['wss_flv', streamContent.wss_flv],
      ];

  for (const [label, raw] of candidates) {
    pushSource(label, raw as string | null | undefined);
  }

  if (videoCodec === 'h264') {
    if (isHttps) {
      pushSource('rtcs', streamContent.rtcs, 'webrtc');
      pushSource('rtc', streamContent.rtc, 'webrtc');
    } else {
      pushSource('rtc', streamContent.rtc, 'webrtc');
      pushSource('rtcs', streamContent.rtcs, 'webrtc');
    }
  }

  const rtmpUrl = toPlayableUrl(streamContent.rtmp);
  if (rtmpUrl) {
    sources.push({
      label: 'rtmp',
      url: rtmpUrl,
      videoCodec,
      playerEngine: pickLivePlayerEngine({ videoCodec, url: rtmpUrl }),
    });
  }
  return sources;
}

export function pickWvpPlaySource(
  streamContent: Record<string, any> | null | undefined,
  options?: {
    isHttps?: boolean;
    toBrowserPlayUrl?: (url?: string | null) => string | null;
  },
): WvpPlaySource | null {
  const source = pickWvpPlaySources(streamContent, options)[0];
  if (!source) return null;
  return {
    url: source.url,
    videoCodec: source.videoCodec,
    playerEngine: source.playerEngine,
  };
}
