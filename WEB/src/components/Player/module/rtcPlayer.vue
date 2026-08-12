<template>
  <div id="rtcPlayer" style="width: 100%; height: 100%; background-color: #000c17">
    <video id="webRtcPlayerBox" controls autoplay style="text-align: left">
      Your browser is too old which doesn't support HTML5 video.
    </video>
  </div>
</template>

<script lang="ts" setup>
  // @ts-nocheck
  import { onMounted, onUnmounted, watch } from 'vue'
  import { getWebrtcNatConfig, reportDevicePlayError, reportDevicePlayReady } from '@/api/device/camera'

  let timer: number
  let webrtcPlayer: any
  let webrtcNatConfig: any = null
  let webrtcNatConfigLoaded = false
  const props = defineProps({
    playUrl: {
      type: String,
      required: true,
    },
    hasAudio: {
      type: Boolean,
      required: true,
    },
    deviceId: {
      type: String,
      default: '',
    },
  })
  watch(
    () => props.playUrl,
    (newPlayerUrl) => {
      console.log('watch== ' + newPlayerUrl)
      pause()
      void play(newPlayerUrl)
    },
  )
  onMounted(() => {
    console.log('player play url: ' + props.playUrl)
    if (props.playUrl) {
      void play(props.playUrl)
    }
  })
  const loadWebrtcNatConfig = async () => {
    if (webrtcNatConfigLoaded) {
      return webrtcNatConfig
    }
    webrtcNatConfigLoaded = true
    try {
      const response = await getWebrtcNatConfig()
      webrtcNatConfig = (response as any)?.data?.data ?? (response as any)?.data ?? response ?? null
    } catch (error) {
      console.warn('load WebRTC NAT config failed', error)
      webrtcNatConfig = null
    }
    return webrtcNatConfig
  }

  const resolveWebrtcPlayUrl = (rawUrl: string, natConfig: any) => {
    const trimmed = (rawUrl || '').trim()
    if (!trimmed) {
      return trimmed
    }
    try {
      const parsed = new URL(trimmed, window.location.href)
      if (natConfig?.public_host) {
        parsed.hostname = natConfig.public_host
      }
      const shouldUseSecure =
        !!natConfig?.prefer_wss ||
        !!natConfig?.require_secure_context ||
        window.location.protocol === 'https:'
      if (shouldUseSecure) {
        if (parsed.protocol === 'http:') parsed.protocol = 'https:'
        if (parsed.protocol === 'ws:') parsed.protocol = 'wss:'
        if (parsed.protocol === 'rtc:') parsed.protocol = 'rtcs:'
      }
      return parsed.toString()
    } catch (error) {
      console.warn('resolve WebRTC play URL failed', error)
      return trimmed
    }
  }

  const reportWebrtcPlayError = async (
    reasonCode: string,
    reasonMessage: string,
    sourceEvent: string,
  ) => {
    const deviceId = (props.deviceId || '').trim()
    if (!deviceId) {
      return
    }
    try {
      await reportDevicePlayError(deviceId, {
        protocol: 'webrtc',
        play_url: props.playUrl,
        reason_code: reasonCode,
        reason_message: reasonMessage,
        source_event: sourceEvent,
      })
    } catch {
      /* status reporting must not block playback */
    }
  }

  const reportWebrtcPlayReady = async (playUrl: string) => {
    const deviceId = (props.deviceId || '').trim()
    if (!deviceId) {
      return
    }
    try {
      await reportDevicePlayReady(deviceId, {
        protocol: 'webrtc',
        play_url: playUrl,
        reason_code: 'webrtc_remote_stream_ready',
        reason_message: 'WebRTC remote stream is playing',
        source_event: 'webrtc.remote.stream',
      })
    } catch {
      /* status reporting must not block playback */
    }
  }

  const play = async (url: string) => {
    console.log('rtc-play: ' + url)
    const natConfig = await loadWebrtcNatConfig()
    const resolvedUrl = resolveWebrtcPlayUrl(url, natConfig)
    webrtcPlayer = new (window as any).ZLMRTCClient.Endpoint({
      element: document.getElementById('webRtcPlayerBox'),
      debug: true,
      zlmsdpUrl: resolvedUrl,
      simulecast: false,
      useCamera: false,
      audioEnable: false,
      videoEnable: false,
      recvOnly: true,
      resolution: [1920, 1080],
      usedatachannel: false,
      pcConfig: natConfig?.iceServers?.length ? { iceServers: natConfig.iceServers } : undefined,
    })
    webrtcPlayer.on((window as any).ZLMRTCClient.Events.WEBRTC_ICE_CANDIDATE_ERROR, (e) => {
      const message = 'ICE candidate negotiation failed: ' + e.toString()
      console.error(message)
      eventcallbacK('ICE ERROR', message)
      void reportWebrtcPlayError('webrtc_ice_candidate_error', message, 'webrtc.ice.candidate')
    })

    webrtcPlayer.on((window as any).ZLMRTCClient.Events.WEBRTC_ON_REMOTE_STREAMS, (e) => {
      console.log('play success', e.streams)
      eventcallbacK('playing', 'play success')
      void reportWebrtcPlayReady(resolvedUrl)
    })

    webrtcPlayer.on(
      (window as any).ZLMRTCClient.Events.WEBRTC_OFFER_ANWSER_EXCHANGE_FAILED,
      (e) => {
        const message = e?.msg ? `offer answer exchange failed: ${e.msg}` : 'offer answer exchange failed'
        console.error(message, e)
        eventcallbacK('OFFER ANSWER ERROR ', message)
        void reportWebrtcPlayError('webrtc_offer_answer_failed', message, 'webrtc.offer.answer')
        if (e.code == -400 && e.msg == '流不存在') {
          console.log('stream not found')
          timer = window.setTimeout(() => {
            webrtcPlayer.close()
            void play(url)
          }, 100)
        }
      },
    )

    webrtcPlayer.on((window as any).ZLMRTCClient.Events.WEBRTC_ON_LOCAL_STREAM, () => {
      eventcallbacK('LOCAL STREAM', 'local stream ready')
    })
  }

  const eventcallbacK = (type, message) => {
    console.log('player event callback')
    console.log(type)
    console.log(message)
  }

  const pause = () => {
    if (webrtcPlayer != null) {
      webrtcPlayer.close()
      webrtcPlayer = null
    }
  }

  onUnmounted(() => {
    clearTimeout(timer)
    pause()
  })
</script>
