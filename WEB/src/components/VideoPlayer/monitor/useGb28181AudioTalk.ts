import { ref, onUnmounted } from 'vue';
import { useMessage } from '@/hooks/web/useMessage';
import { startGbAudioBroadcast, stopGbAudioBroadcast } from '@/api/device/gb28181';
import { rewriteStreamHostToPageHost } from '@/views/camera/utils/devicePlay';
import { resolveGbAudioBroadcastStreamInfo } from './gb28181AudioTalkResponse';

export type AudioTalkStatus = 'idle' | 'connecting' | 'active' | 'error';

const GB28181_TALK_CONNECTION_TIMEOUT_MS = 12 * 1000;

declare global {
  interface Window {
    ZLMRTCClient?: any;
  }
}

function pickWebRtcPushUrl(streamInfo: Record<string, any> | null | undefined): string | null {
  if (!streamInfo) return null;
  const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const raw = isHttps ? streamInfo.rtcs || streamInfo.rtc : streamInfo.rtc || streamInfo.rtcs;
  if (!raw) return null;
  const url = rewriteStreamHostToPageHost(String(raw));
  if (!url) return null;
  if (url.includes('type=push')) return url;
  return url.includes('?') ? `${url}&type=push` : `${url}?type=push`;
}

export function useGb28181AudioTalk(
  sipDeviceId: () => string | undefined,
  channelId: () => string | undefined,
) {
  const { createMessage } = useMessage();
  const status = ref<AudioTalkStatus>('idle');
  const infoText = ref('点击「开始对讲」建立国标语音通道');
  const volume = ref(100);
  const noiseSuppression = ref(true);
  const echoCancellation = ref(true);
  const level = ref(0);

  let localStream: MediaStream | null = null;
  let pushClient: any = null;
  let levelTimer: ReturnType<typeof setInterval> | null = null;
  let analyserCtx: AudioContext | null = null;
  let connectionTimer: ReturnType<typeof setTimeout> | null = null;

  function clearConnectionTimer() {
    if (connectionTimer) {
      clearTimeout(connectionTimer);
      connectionTimer = null;
    }
  }

  function stopLevelCheck() {
    if (levelTimer) {
      clearInterval(levelTimer);
      levelTimer = null;
    }
    if (analyserCtx) {
      analyserCtx.close().catch(() => {});
      analyserCtx = null;
    }
    level.value = 0;
  }

  function startLevelCheck(stream: MediaStream) {
    stopLevelCheck();
    analyserCtx = new AudioContext();
    const analyser = analyserCtx.createAnalyser();
    const source = analyserCtx.createMediaStreamSource(stream);
    source.connect(analyser);
    analyser.fftSize = 256;
    const data = new Uint8Array(analyser.frequencyBinCount);
    levelTimer = setInterval(() => {
      analyser.getByteFrequencyData(data);
      let sum = 0;
      for (let i = 0; i < data.length; i++) sum += data[i];
      level.value = Math.min(5, Math.floor((sum / data.length / 255) * 5 * (volume.value / 50)));
    }, 120);
  }

  async function start() {
    const deviceId = sipDeviceId();
    const chId = channelId();
    if (!deviceId || !chId) return;
    if (status.value === 'connecting' || status.value === 'active') return;

    if (!window.ZLMRTCClient) {
      createMessage.error('ZLM WebRTC 客户端未加载');
      return;
    }

    status.value = 'connecting';
    infoText.value = '启动国标语音广播...';
    try {
      const res: any = await startGbAudioBroadcast(deviceId, chId, true);
      const streamInfo = resolveGbAudioBroadcastStreamInfo(res);
      const pushUrl = pickWebRtcPushUrl(streamInfo);
      if (!pushUrl) throw new Error('未获取到 WebRTC 推流地址');

      pushClient = new window.ZLMRTCClient.Endpoint({
        element: null,
        debug: false,
        zlmsdpUrl: pushUrl,
        simulecast: false,
        useCamera: false,
        audioEnable: true,
        videoEnable: false,
        recvOnly: false,
      });

      const ZLM = window.ZLMRTCClient;
      pushClient.on(ZLM.Events.WEBRTC_ON_LOCAL_STREAM, (stream: MediaStream) => {
        localStream = stream;
        const track = stream.getAudioTracks()[0];
        track?.applyConstraints({
          echoCancellation: echoCancellation.value,
          noiseSuppression: noiseSuppression.value,
          autoGainControl: true,
          channelCount: 1,
        }).catch(() => {});
      });

      pushClient.on(ZLM.Events.WEBRTC_ON_CONNECTION_STATE_CHANGE, (state: string) => {
        if (state === 'connected') {
          clearConnectionTimer();
          status.value = 'active';
          infoText.value = '国标语音对讲已连接';
          if (localStream) startLevelCheck(localStream);
        } else if (state === 'failed' || state === 'disconnected') {
          void failConnection('WebRTC 推流连接失败');
        }
      });

      pushClient.on(ZLM.Events.CAPTURE_STREAM_FAILED, () => {
        void failConnection('无法获取麦克风权限');
      });
      pushClient.on(ZLM.Events.WEBRTC_OFFER_ANWSER_EXCHANGE_FAILED, () => {
        void failConnection('WebRTC 协商失败');
      });
      pushClient.on(ZLM.Events.WEBRTC_ICE_CANDIDATE_ERROR, () => {
        void failConnection('WebRTC 网络连接失败');
      });

      connectionTimer = setTimeout(() => {
        void failConnection('语音对讲连接超时，请检查摄像机对讲能力和网络');
      }, GB28181_TALK_CONNECTION_TIMEOUT_MS);
    } catch (e: any) {
      await failConnection(e?.message || '启动国标对讲失败', false);
    }
  }

  async function failConnection(message: string, callApi = true) {
    if (status.value !== 'connecting' && status.value !== 'active') return;
    status.value = 'error';
    infoText.value = message;
    await stopInternal(callApi, false);
    createMessage.error(message);
  }

  async function stopInternal(callApi = true, resetState = true) {
    clearConnectionTimer();
    stopLevelCheck();
    if (pushClient) {
      try {
        pushClient.close();
      } catch {
        /* ignore */
      }
      pushClient = null;
    }
    localStream?.getTracks().forEach((t) => t.stop());
    localStream = null;

    if (callApi) {
      const deviceId = sipDeviceId();
      const chId = channelId();
      if (deviceId && chId) {
        try {
          await stopGbAudioBroadcast(deviceId, chId);
        } catch {
          /* ignore */
        }
      }
    }
    if (resetState) {
      status.value = 'idle';
      infoText.value = '对讲已结束';
    }
  }

  async function stop() {
    await stopInternal(true);
  }

  function updateVolume(v: number) {
    volume.value = v;
  }

  onUnmounted(() => {
    stopInternal(true);
  });

  return {
    status,
    infoText,
    volume,
    noiseSuppression,
    echoCancellation,
    level,
    start,
    stop,
    updateVolume,
  };
}
