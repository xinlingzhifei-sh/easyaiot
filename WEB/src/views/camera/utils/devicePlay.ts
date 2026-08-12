import type { DeviceAccessProtocol, DeviceInfo, MonitorTreeDeviceNode } from '@/api/device/camera';
import { getDeviceInfo, reportDevicePlayError, reportDevicePlayReady } from '@/api/device/camera';
import { playByDeviceAndChannel } from '@/api/device/gb28181';
import {
  formatCameraDeviceLabel,
  gb28181VirtualDeviceId,
  getGb28181PlayIds,
  isGb28181Device,
  shouldPlayViaGb28181,
} from './deviceLabel';
import { isProtectedStreamUrl, signStreamUrl } from './streamTicket';
import {
  pickLivePlayerEngine,
  pickWvpPlaySource as pickWvpLivePlayerSource,
  pickWvpPlaySources as pickWvpLivePlayerSources,
  type WvpPlaySource,
  type WvpPlaySourceOption,
} from './livePlayer';
import {
  convertRtmpToHttp as convertRtmpToHttpForBrowser,
  rewriteStreamUrlForBrowser as rewriteStreamUrlForBrowserForBrowser,
} from './streamUrlRewrite';

export type DevicePlayModalOpener = (visible: boolean, data: Record<string, any>) => void;

function parseProviderJson(value: unknown): Record<string, any> | null {
  if (!value) return null;
  if (typeof value === 'object') return value as Record<string, any>;
  if (typeof value !== 'string') return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

/** 从设备记录中提取火山 RTC（volc）播放地址 */
export function extractVolcLiveUrl(record: Record<string, any> | null | undefined): string | null {
  if (!record) return null;
  const provider =
    parseProviderJson(record.provider) ||
    parseProviderJson(record.live_provider) ||
    parseProviderJson(record.providerJson) ||
    parseProviderJson(record.provider_json);

  const providerType = String(
    provider?.url_type || provider?.type || record.providerType || record.urlType || '',
  ).toLowerCase();
  const providerUrl = String(provider?.url || '').trim();
  if (providerType === 'volc' && providerUrl) return providerUrl;

  for (const candidate of [record.source, record.url, record.directUrl]) {
    const value = String(candidate || '').trim();
    if (!value) continue;
    if (value.startsWith('volc://')) return decodeURIComponent(value.slice('volc://'.length));
  }
  return null;
}

export function isGb28181DeviceRecord(record: { source?: string | null; device_kind?: string }) {
  return isGb28181Device(record.source, record.device_kind);
}

export function hasDirectPlayStream(record: Record<string, any>, ai = false): boolean {
  if (isGb28181DeviceRecord(record)) return false;
  if ((record as { device_kind?: string }).device_kind === 'gb28181_sip') return false;
  if (!ai && extractVolcLiveUrl(record as Record<string, any>)) return true;
  if (ai) {
    return !!(record.ai_http_stream || record.ai_rtmp_stream);
  }
  return !!(record.http_stream || record.rtmp_stream);
}

/** 设备是否具备可播放流（原始流、AI 流或国标点播） */
export function hasPlayableStream(record: Record<string, any>): boolean {
  if (shouldPlayViaGb28181(record)) return true;
  return hasDirectPlayStream(record) || hasDirectPlayStream(record, true);
}

type DirectStreamFields = Partial<Pick<
  DeviceInfo,
  'http_stream' | 'rtmp_stream' | 'ai_http_stream' | 'ai_rtmp_stream'
>>;

export interface DirectPlayUrlResult {
  url: string | null;
  /** 启用 AI 时，AI 地址不可播则回退原始流 */
  fallbackUrl?: string | null;
  /** 已探测到 AI 流在推流，播放器超时后再回退原始流 */
  preferAi?: boolean;
  /** 首帧先播原始流后，后台探测就绪可升级的 AI 地址 */
  pendingAiUrl?: string | null;
}

/** 探测 AI 流是否在 ZLM 上就绪（毫秒） */
export const AI_STREAM_PROBE_MS = 1200;
/** 直连 AI 流起播超时后回退原始流（毫秒，仅 preferAi 时生效） */
export const AI_PLAY_FALLBACK_MS = 2500;
/** Jessibuca 播 /ai 且可回退时，加载/心跳超时（秒），尽快触发 stream-error */
export const AI_STREAM_LOAD_TIMEOUT_SEC = 3;
export const AI_STREAM_HEART_TIMEOUT_SEC = 8;

/** 将服务端生成的 127.0.0.1/localhost 流地址改写为当前页面主机名，便于浏览器拉流 */
export function rewriteStreamUrlForBrowser(url: string): string {
  return rewriteStreamUrlForBrowserForBrowser(url);
}

/**
 * 将流地址的主机名+端口改写为当前页面的 host（hostname:port），便于浏览器拉流。
 * 例如页面在 http://localhost:8888 打开时，
 * http://33.150.1.104:8080/ai/xxx.flv -> http://localhost:8888/ai/xxx.flv
 * 仅替换 host，协议与路径保持不变。
 * forcePageProxy 用于明确知道当前页面 nginx 已代理媒体路径的入口，避免反向代理页面
 * 仍按服务端返回的远端 host:port 直连媒体服务。
 */
export function rewriteStreamHostToPageHost(
  url: string,
  options?: { forcePageProxy?: boolean },
): string {
  const trimmed = url?.trim();
  if (!options?.forcePageProxy || !trimmed || typeof window === 'undefined') {
    return rewriteStreamUrlForBrowserForBrowser(trimmed);
  }

  try {
    const parsed = new URL(trimmed);
    const pageHostname = window.location.hostname;
    if (!pageHostname) return trimmed;
    parsed.hostname = pageHostname;
    parsed.port = window.location.port || '';
    return parsed.toString();
  } catch {
    return trimmed;
  }
}

/**
 * 规范化 Jessibuca 播放地址。
 * SRS 的 /live、/ai 经页面 nginx 或 Vite 代理时须用 HTTP-FLV（GET 长连接）；
 * 改为 ws:// 时 Vite 开发环境常握手失败（Unexpected response code: 200）。
 * 国标 /rtp 仍保留 ws-flv（ZLM）。
 */
export function normalizeJessibucaPlayUrl(url: string): string {
  const trimmed = url?.trim();
  if (!trimmed || typeof window === 'undefined') return trimmed;

  try {
    const parsed = new URL(trimmed);
    if (/^\/(ai|live)\//i.test(parsed.pathname)) {
      if (parsed.protocol === 'ws:') parsed.protocol = 'http:';
      if (parsed.protocol === 'wss:') parsed.protocol = 'https:';
      // https 页面直连 http-flv 会被浏览器按 mixed-content 拦截。
      // 仅升级已改写成页面 host 的地址（单机经页面 nginx 反代，随页面出 https）；
      // 远端集群节点 host 未改写、其 8080 未必有 TLS，不能盲目升级。
      if (
        window.location.protocol === 'https:' &&
        parsed.protocol === 'http:' &&
        parsed.host === window.location.host
      ) {
        parsed.protocol = 'https:';
      }
      return parsed.toString();
    }
    return trimmed;
  } catch {
    return trimmed;
  }
}

/** @deprecated 仅 ZLM /rtp 等已确认支持 WS 代理的场景使用；SRS /live 请用 HTTP-FLV */
export function preferWsFlvForJessibuca(url: string): string {
  const trimmed = url?.trim();
  if (!trimmed || typeof window === 'undefined') return trimmed;

  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol === 'ws:' || parsed.protocol === 'wss:') return trimmed;
    if (!/\.flv(\?|$)/i.test(parsed.pathname)) return trimmed;
    if (parsed.protocol === 'http:') {
      parsed.protocol = 'ws:';
      return parsed.toString();
    }
    if (parsed.protocol === 'https:') {
      parsed.protocol = 'wss:';
      return parsed.toString();
    }
    return trimmed;
  } catch {
    return trimmed;
  }
}

/** fetch 探测流可用性须用 HTTP(S)，不能走 WS */
export function flvUrlForHttpProbe(url: string): string {
  const trimmed = url?.trim();
  if (!trimmed) return trimmed;
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol === 'ws:') parsed.protocol = 'http:';
    else if (parsed.protocol === 'wss:') parsed.protocol = 'https:';
    return parsed.toString();
  } catch {
    return trimmed;
  }
}

/** RTMP 转 HTTP-FLV（Jessibuca 浏览器端需 HTTP/WS 地址） */
export function convertRtmpToHttp(rtmpUrl: string): string | null {
  return convertRtmpToHttpForBrowser(rtmpUrl);
}

function toBrowserPlayUrl(stream?: string | null): string | null {
  const trimmed = stream?.trim();
  if (!trimmed) return null;
  const httpUrl = trimmed.startsWith('rtmp://') ? convertRtmpToHttp(trimmed) : trimmed;
  if (!httpUrl) return null;
  // 所有播放地址统一走当前页面 host:port，便于不同环境下浏览器直接拉流（HTTP-FLV）
  return normalizeJessibucaPlayUrl(rewriteStreamHostToPageHost(httpUrl));
}

function inferAccessProtocolFromUrl(url?: string | null): DeviceAccessProtocol {
  const raw = url?.trim();
  if (!raw) return 'http_flv';
  try {
    const parsed = new URL(raw, 'http://localhost');
    const scheme = parsed.protocol.replace(':', '').toLowerCase();
    const path = parsed.pathname.toLowerCase();
    const query = parsed.search.toLowerCase();
    if (scheme === 'rtsp') return 'rtsp';
    if (scheme === 'rtmp') return 'rtmp';
    if (scheme === 'webrtc' || scheme === 'rtc' || path.includes('webrtc') || query.includes('rtc')) {
      return 'webrtc';
    }
    return 'http_flv';
  } catch {
    return 'http_flv';
  }
}

async function safeReportDevicePlayReady(
  deviceId: string,
  url: string,
  protocol?: DeviceAccessProtocol,
  ai = false,
) {
  try {
    await reportDevicePlayReady(deviceId, {
      protocol: protocol || inferAccessProtocolFromUrl(url),
      play_url: url,
      ai,
    });
  } catch {
    /* status reporting must not block playback */
  }
}

async function safeReportDevicePlayError(
  deviceId: string,
  protocol: DeviceAccessProtocol,
  reasonCode: string,
  reasonMessage: string,
) {
  try {
    await reportDevicePlayError(deviceId, {
      protocol,
      reason_code: reasonCode,
      reason_message: reasonMessage,
    });
  } catch {
    /* status reporting must not block playback */
  }
}

/** 是否为算法任务输出的 AI 流（检测框烧录在此路流上） */
export function isAiStreamPlayUrl(url?: string | null): boolean {
  if (!url) return false;
  return /\/ai\//i.test(url);
}

function pickVideoPlayUrl(device: DirectStreamFields): string | null {
  return toBrowserPlayUrl(device.http_stream) || toBrowserPlayUrl(device.rtmp_stream);
}

function pickAiPlayUrl(device: DirectStreamFields): string | null {
  return toBrowserPlayUrl(device.ai_http_stream) || toBrowserPlayUrl(device.ai_rtmp_stream);
}

/** 探测时判定"真有推流"所需的最小媒体字节数（FLV 头仅 13B，无推流方时只回头部就停） */
const PROBE_MIN_MEDIA_BYTES = 1024;

/**
 * 快速探测流是否可播（避免无算法任务时长时间等待空 AI 地址）。
 * 仅返回 200/FLV 头不足为据：SRS 对任何 FLV 请求都会临时创建空源并回头部，
 * 因此必须确认在超时窗口内确有媒体数据流过，才认定 AI 流已就绪。
 * 探测失败时返回 false，调用方应直接播原始流。
 */
export async function probeStreamPlayable(
  url: string,
  timeoutMs = AI_STREAM_PROBE_MS,
): Promise<boolean> {
  let target = url?.trim();
  if (!target || typeof window === 'undefined') return false;
  target = flvUrlForHttpProbe(target);
  // 探测直连 fetch /ai 地址，受 secure_link 保护，需先签名（开启强制校验时未签名恒 403）。
  // 签发失败则降级探测未签名地址：强制校验关闭时仍能正常探测，开启时会 403 -> 探测返回 false -> 回退原始流。
  if (isProtectedStreamUrl(target)) {
    try {
      target = await signStreamUrl(target);
    } catch {
      /* 降级：保留未签名地址继续探测 */
    }
  }
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(target, {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store',
    });
    if (res.status === 404 || res.status === 403) return false;
    if (!res.ok && res.status !== 206) return false;
    if (!res.body) return false;

    const reader = res.body.getReader();
    let received = 0;
    try {
      while (received < PROBE_MIN_MEDIA_BYTES) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value) received += value.length;
      }
    } finally {
      // 停止拉流，释放 SRS 上的临时消费连接
      try {
        await reader.cancel();
      } catch {
        /* ignore */
      }
    }
    return received >= PROBE_MIN_MEDIA_BYTES;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timer);
  }
}

/** 直连设备播放地址：启用 AI 时优先 AI 流，无 AI 地址则回退原始流；未启用时仅原始流 */
export async function pickDirectPlayUrl(
  device: DirectStreamFields,
  enableAi = false,
): Promise<string | null> {
  return (await pickDirectPlayUrls(device, enableAi)).url;
}

export async function pickDirectPlayUrls(
  device: DirectStreamFields,
  enableAi = false,
): Promise<DirectPlayUrlResult> {
  const videoUrl = pickVideoPlayUrl(device);
  if (!enableAi) {
    return { url: videoUrl };
  }

  const aiUrl = pickAiPlayUrl(device);
  if (!aiUrl) {
    return { url: videoUrl };
  }
  if (aiUrl === videoUrl) {
    return { url: aiUrl };
  }
  if (!videoUrl) {
    return { url: aiUrl, preferAi: true };
  }

  // 启用 AI：先 instant 播 /live，后台 1.2s 探测 /ai 就绪后无感升级；/ai 失败则 3s 内回退 /live
  return { url: videoUrl, pendingAiUrl: aiUrl };
}

/**
 * 首帧已播原始流后，后台探测 AI 就绪再升级（分屏/大屏/弹窗共用）。
 */
export function schedulePendingAiStreamUpgrade(
  aiUrl: string,
  fallbackUrl: string,
  shouldUpgrade: () => boolean,
  onUpgrade: () => void,
): void {
  const ai = aiUrl?.trim();
  const fb = fallbackUrl?.trim();
  if (!ai || !fb || ai === fb) return;
  void probeStreamPlayable(ai, AI_STREAM_PROBE_MS).then((ready) => {
    if (!ready || !shouldUpgrade()) return;
    onUpgrade();
  });
}

export function supportsRtspForward(record: Record<string, any>): boolean {
  return !isGb28181DeviceRecord(record);
}

/** 从 WVP 点播结果中选取浏览器可播地址（HTTPS 页优先 wss/https，并做 localhost 改写） */
export function pickWvpPlaySource(
  streamContent: Record<string, any> | null | undefined,
): WvpPlaySource | null {
  return pickWvpLivePlayerSource(streamContent, { toBrowserPlayUrl });
}

export function pickWvpPlaySources(
  streamContent: Record<string, any> | null | undefined,
): WvpPlaySourceOption[] {
  return pickWvpLivePlayerSources(streamContent, { toBrowserPlayUrl });
}

export function pickWvpPlayUrl(streamContent: Record<string, any> | null | undefined): string | null {
  return pickWvpPlaySource(streamContent)?.url ?? null;
}

export async function resolveGb28181StreamUrl(
  sipDeviceId: string,
  channelId: string,
): Promise<string | null> {
  const deviceId = gb28181VirtualDeviceId(sipDeviceId, channelId);
  const url = (await resolveGb28181StreamSource(sipDeviceId, channelId))?.url ?? null;
  if (url) {
    void safeReportDevicePlayReady(deviceId, url, 'gb28181');
  } else {
    void safeReportDevicePlayError(
      deviceId,
      'gb28181',
      'gb28181_play_url_unavailable',
      'WVP play returned no playable stream',
    );
  }
  return url;
}

export async function resolveGb28181StreamSource(
  sipDeviceId: string,
  channelId: string,
): Promise<WvpPlaySource | null> {
  return (await resolveGb28181StreamSources(sipDeviceId, channelId))[0] ?? null;
}

export async function resolveGb28181StreamSources(
  sipDeviceId: string,
  channelId: string,
): Promise<WvpPlaySourceOption[]> {
  const res = await playByDeviceAndChannel(sipDeviceId, channelId);
  const streamContent = (res as any)?.data?.data ?? (res as any)?.data;
  return pickWvpPlaySources(streamContent);
}

export interface GbChannelPlayUrlResult {
  url: string | null;
  fallbackUrl?: string | null;
  preferAi?: boolean;
  playerEngine?: WvpPlaySource['playerEngine'] | null;
  videoCodec?: WvpPlaySource['videoCodec'] | null;
  playSources?: WvpPlaySourceOption[] | null;
  pendingAiUrl?: string | null;
}

function buildManualWvpPlaySource(url?: string | null): GbChannelPlayUrlResult {
  const trimmed = url?.trim();
  return { url: trimmed || null, playerEngine: null, videoCodec: null };
}

/** 加载国标通道对应的 device 表记录（含 ai_http_stream） */
export async function loadGbChannelSyncedDevice(
  sipDeviceId: string,
  channelId: string,
  synced?: Record<string, any> | null,
): Promise<MonitorTreeDeviceNode | null> {
  if (synced?.ai_http_stream?.trim() || synced?.ai_rtmp_stream?.trim()) {
    return synced as MonitorTreeDeviceNode;
  }
  const syncedId = String(synced?.id ?? '').trim();
  const lookupId =
    syncedId && !syncedId.startsWith('gb_ch_')
      ? syncedId
      : gb28181VirtualDeviceId(sipDeviceId, channelId);
  try {
    const res = await getDeviceInfo(lookupId);
    const device = (res as any)?.data ?? res;
    return device?.id
      ? (device as MonitorTreeDeviceNode)
      : (synced as MonitorTreeDeviceNode | null | undefined) ?? null;
  } catch {
    return (synced as MonitorTreeDeviceNode | null | undefined) ?? null;
  }
}

/**
 * 国标通道播放地址：启用 AI 时优先 ai_http_stream（算法烧录检测框），否则 WVP 点播原始流。
 */
export async function resolveGbChannelPlayUrls(
  sipDeviceId: string,
  channelId: string,
  options?: {
    enableAi?: boolean;
    synced?: Record<string, any> | null;
    wvpUrl?: string | null;
  },
): Promise<GbChannelPlayUrlResult> {
  const enableAi = options?.enableAi ?? false;
  const wvpSourcePromise: Promise<GbChannelPlayUrlResult> =
    options?.wvpUrl != null
      ? Promise.resolve(buildManualWvpPlaySource(options.wvpUrl))
      : resolveGb28181StreamSources(sipDeviceId, channelId).then((sources) => {
          const source = sources[0];
          if (!source) return { url: null, playSources: sources };
          return {
            url: source.url,
            playerEngine: source.playerEngine,
            videoCodec: source.videoCodec,
            playSources: sources,
          };
        });

  if (!enableAi) {
    return wvpSourcePromise;
  }

  const [wvpSource, synced] = await Promise.all([
    wvpSourcePromise,
    loadGbChannelSyncedDevice(sipDeviceId, channelId, options?.synced ?? null),
  ]);

  if (synced) {
    const { url, fallbackUrl, preferAi, pendingAiUrl } = await pickDirectPlayUrls(
      synced as DirectStreamFields,
      true,
    );
    if (url) {
      if (!isAiStreamPlayUrl(url) && !pendingAiUrl && wvpSource.url) {
        return wvpSource;
      }
      const aiPlayerEngine = pickLivePlayerEngine({
        url,
        videoCodec: wvpSource.videoCodec,
      });
      if (synced.id) {
        void safeReportDevicePlayReady(synced.id, url, inferAccessProtocolFromUrl(url), !!preferAi);
      }
      return {
        url,
        fallbackUrl: preferAi ? wvpSource.url : fallbackUrl ?? wvpSource.url,
        preferAi,
        playerEngine: aiPlayerEngine,
        videoCodec: wvpSource.videoCodec,
        playSources: wvpSource.playSources,
        pendingAiUrl,
      };
    }
  }

  return wvpSource;
}

export interface DialogPlayerOpenOptions {
  /** 启用 AI 时优先 AI 流，无则回退原始流；默认 true */
  enableAi?: boolean;
}

export async function openDeviceInDialogPlayer(
  openModal: DevicePlayModalOpener,
  record: Record<string, any>,
  options?: DialogPlayerOpenOptions,
): Promise<boolean> {
  const enableAi = options?.enableAi ?? true;
  const name = formatCameraDeviceLabel(record);

  const gbIds = getGb28181PlayIds(record as Record<string, any>);
  if (gbIds || shouldPlayViaGb28181(record)) {
    const sipDeviceId =
      gbIds?.sipDeviceId ?? String(record.deviceIdentification || record.sip_device_id || '').trim();
    const channelId =
      gbIds?.channelId ??
      String(record.channelId || record.presetPos || record.channel_id || '').trim();
    if (!sipDeviceId || !channelId) return false;

    const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolveGbChannelPlayUrls(sipDeviceId, channelId, {
      enableAi,
      synced: record,
    });
    if (!url) return false;

    openModal(true, {
      ...record,
      name,
      deviceIdentification: sipDeviceId,
      channelId,
      http_stream: url,
      _fallbackUrl: fallbackUrl ?? null,
      _preferAi: preferAi ?? false,
      _pendingAiUrl: pendingAiUrl ?? null,
      _enableAi: enableAi,
    });
    return true;
  }

  if (!hasPlayableStream(record)) return false;

  const { url, fallbackUrl, preferAi, pendingAiUrl } = await pickDirectPlayUrls(record, enableAi);
  if (!url) return false;

  openModal(true, {
    ...record,
    name,
    http_stream: url,
    _fallbackUrl: fallbackUrl ?? null,
    _preferAi: preferAi ?? false,
    _pendingAiUrl: pendingAiUrl ?? null,
    _enableAi: enableAi,
  });
  return true;
}

export async function resolveMonitorPlayUrl(
  device: DeviceInfo,
  streamType: 'video' | 'ai' = 'video',
): Promise<string | null> {
  if (streamType === 'ai') {
    const url = pickAiPlayUrl(device);
    if (url) {
      void safeReportDevicePlayReady(device.id, url, inferAccessProtocolFromUrl(url), true);
    } else {
      void safeReportDevicePlayError(device.id, 'http_flv', 'ai_play_url_unavailable', 'AI playback URL is not configured');
    }
    return url;
  }

  const gbIds = getGb28181PlayIds(device as Record<string, any>);
  if (gbIds) {
    return resolveGb28181StreamUrl(gbIds.sipDeviceId, gbIds.channelId);
  }

  const url = pickVideoPlayUrl(device);
  if (url) {
    void safeReportDevicePlayReady(device.id, url, inferAccessProtocolFromUrl(url));
  } else {
    void safeReportDevicePlayError(device.id, 'http_flv', 'play_url_unavailable', 'Playback URL is not configured');
  }
  return url;
}
