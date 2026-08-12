import {
  resolveAlertRecordVideoUrl,
  resolveAlertVideoUrl,
  type AlertRecordLike,
} from '@/utils/alertRecord';
import { useMessage } from '@/hooks/web/useMessage';

const { createMessage } = useMessage();
const PLAYBACK_LOADING_KEY = 'alert-record-playback';

export type AlertRecordModalMethods = {
  openModal: (open?: boolean, data?: any, openOnSet?: boolean) => void;
  closeModal?: () => void;
  setModalProps?: (props: Record<string, any>) => void;
};

export type AlertRecordPlayInput = AlertRecordLike & {
  device_id?: string | number;
  time?: string;
  seek_time?: string | null;
  record_start_time?: string | null;
  playback_offset_seconds?: number | null;
  video_url?: string | null;
  url?: string | null;
};

type AuthorizedPlaybackResolution = {
  claimed: boolean;
  url: string | null;
};

const AUTHORIZED_PLAYBACK_TICKET_FIELDS = [
  'yf_service_id',
  'yf_user_id',
  'yf_tenant_id',
  'yf_camera_id',
  'yf_timestamp',
  'yf_nonce',
] as const;

/** 每次播放递增，避免 useModal 在快速连续 openModal 时合并/跳过回调 */
let playbackSeq = 0;

function buildModalPayload(
  deviceId: string | number,
  videoUrl: string,
  seq: number,
  pending: boolean,
  seekContext: ReturnType<typeof resolvePlaybackSeekContext>,
) {
  return {
    id: deviceId,
    http_stream: videoUrl,
    ...(seekContext.seekTime ? { seek_time: seekContext.seekTime } : {}),
    ...(seekContext.playbackOffsetSeconds != null
      ? { playback_offset_seconds: seekContext.playbackOffsetSeconds }
      : {}),
    ...(pending ? { _pendingRecord: true as const } : {}),
    _playbackSeq: seq,
  };
}

/** 打开录像弹框前先设置布局，避免首次打开闪退 */
export function prepareAlertRecordModalShell(
  setModalProps?: (props: Record<string, any>) => void,
) {
  setModalProps?.({
    defaultFullscreen: false,
    canFullscreen: false,
    width: 1000,
    title: '录像回放',
    minHeight: 0,
    bodyStyle: { padding: 0 },
    wrapClassName: 'monitor-dialog-wrap monitor-dialog-wrap--vod',
  });
}

function parseTimeMs(value?: string | null): number | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : null;
}

function resolvePlaybackSeekContext(record: AlertRecordPlayInput) {
  const seekTime = record.seek_time || record.time || '';
  const hasExplicitOffset = record.playback_offset_seconds != null;
  const explicitOffset = hasExplicitOffset ? Number(record.playback_offset_seconds) : Number.NaN;
  if (Number.isFinite(explicitOffset) && explicitOffset >= 0) {
    return {
      seekTime,
      playbackOffsetSeconds: Math.round(explicitOffset),
    };
  }

  const seekMs = parseTimeMs(seekTime);
  const startMs = parseTimeMs(record.record_start_time);
  if (seekMs == null || startMs == null || seekMs < startMs) {
    return {
      seekTime,
      playbackOffsetSeconds: null,
    };
  }

  return {
    seekTime,
    playbackOffsetSeconds: Math.round((seekMs - startMs) / 1000),
  };
}

function isAllowedPlaybackPath(url: URL, apiUrl: URL): boolean {
  const apiPrefix = apiUrl.pathname.replace(/\/+$/, '');
  const paths = [url.pathname];
  if (apiPrefix && apiPrefix !== '/' && url.pathname.startsWith(`${apiPrefix}/`)) {
    paths.push(url.pathname.slice(apiPrefix.length));
  }
  return paths.some(
    (path) => path === '/video/alert/record' || path.startsWith('/video/record/'),
  );
}

function resolveAuthorizedPlaybackUrl(record: AlertRecordPlayInput): AuthorizedPlaybackResolution {
  const raw = record.record_path?.trim();
  if (!raw || typeof window === 'undefined') {
    return { claimed: false, url: null };
  }

  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return { claimed: false, url: null };
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return { claimed: false, url: null };
  }

  const claimed = ['yf_ticket', 'yf_action', 'yf_signature'].some((key) =>
    url.searchParams.has(key),
  );
  if (!claimed) {
    return { claimed: false, url: null };
  }

  let apiUrl: URL;
  try {
    apiUrl = new URL(import.meta.env.VITE_GLOB_API_URL || '/', window.location.origin);
  } catch {
    return { claimed: true, url: null };
  }
  const trustedOrigins = new Set([window.location.origin, apiUrl.origin]);
  const expectedCameraId = String(record.device_id ?? '').trim();
  const signature = url.searchParams.get('yf_signature') || '';
  const trusted =
    !url.username &&
    !url.password &&
    !url.hash &&
    trustedOrigins.has(url.origin) &&
    isAllowedPlaybackPath(url, apiUrl) &&
    url.searchParams.get('playback_format') === 'mp4' &&
    url.searchParams.get('yf_ticket') === 'v1' &&
    url.searchParams.get('yf_action') === 'playback' &&
    AUTHORIZED_PLAYBACK_TICKET_FIELDS.every((key) => Boolean(url.searchParams.get(key))) &&
    /^\d+$/.test(url.searchParams.get('yf_timestamp') || '') &&
    /^sha256=[a-f\d]{64}$/i.test(signature) &&
    (!expectedCameraId || url.searchParams.get('yf_camera_id') === expectedCameraId);
  return { claimed: true, url: trusted ? raw : null };
}

/**
 * 在大屏/告警等场景打开告警录像：先解析地址，确认有录像后再打开播放器。
 * mini / standard / full 共用，兼容 MinIO 直链与按设备+时间查询。
 */
export async function playAlertRecordInModal(
  modal: AlertRecordModalMethods,
  record: AlertRecordPlayInput,
): Promise<boolean> {
  const { openModal, closeModal, setModalProps } = modal;
  const seq = ++playbackSeq;
  const seekContext = resolvePlaybackSeekContext(record);

  const authorizedPlayback = resolveAuthorizedPlaybackUrl(record);
  if (authorizedPlayback.claimed) {
    if (!authorizedPlayback.url) {
      return false;
    }
    prepareAlertRecordModalShell(setModalProps);
    openModal(
      true,
      buildModalPayload(
        record.device_id ?? 0,
        authorizedPlayback.url,
        seq,
        false,
        seekContext,
      ),
    );
    return true;
  }

  const directRaw = record.video_url || record.url;
  if (directRaw) {
    const videoUrl = resolveAlertVideoUrl(String(directRaw).trim());
    if (videoUrl) {
      prepareAlertRecordModalShell(setModalProps);
      openModal(true, buildModalPayload(record.device_id ?? 0, videoUrl, seq, false, seekContext));
      return true;
    }
  }

  const deviceId = record.device_id;
  if (deviceId == null || deviceId === '' || !record.time) {
    return false;
  }

  prepareAlertRecordModalShell(setModalProps);
  createMessage.loading({ content: '正在查询告警录像...', key: PLAYBACK_LOADING_KEY, duration: 0 });
  openModal(true, buildModalPayload(deviceId, '', seq, true, seekContext));

  try {
    const videoUrl = await resolveAlertRecordVideoUrl(record);
    createMessage.destroy(PLAYBACK_LOADING_KEY);
    if (videoUrl) {
      prepareAlertRecordModalShell(setModalProps);
      openModal(true, buildModalPayload(deviceId, videoUrl, seq, false, seekContext));
      return true;
    }
    closeModal?.();
    openModal(false);
    return false;
  } catch (error) {
    createMessage.destroy(PLAYBACK_LOADING_KEY);
    closeModal?.();
    openModal(false);
    throw error;
  }
}
