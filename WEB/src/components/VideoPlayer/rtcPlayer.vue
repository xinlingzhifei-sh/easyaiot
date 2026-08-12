<template>
  <div class="rtc-player">
    <video
      ref="video"
      :id="playerId"
      class="rtc-player__video"
      controls
      autoplay
      playsinline
    >
      Your browser is too old which doesn't support HTML5 video.
    </video>
  </div>
</template>

<script>
export default {
  name: 'rtcPlayer',
  emits: ['stream-error', 'playing'],
  props: ['videoUrl', 'error', 'hasaudio'],
  data() {
    return {
      timer: null,
      firstFrameTimer: null,
      webrtcPlayer: null,
      playerId: `webRtcPlayerBox-${Math.random().toString(36).slice(2)}`,
    };
  },
  mounted() {
    const routeUrl = this.$route?.params?.url ? decodeURIComponent(this.$route.params.url) : '';
    const target = typeof this.videoUrl === 'undefined' ? routeUrl : this.videoUrl;
    this.$nextTick(() => {
      if (target) this.play(target);
    });
  },
  watch: {
    videoUrl(newData) {
      this.pause();
      if (newData) this.play(newData);
    },
  },
  beforeUnmount() {
    this.pause();
  },
  destroyed() {
    this.pause();
  },
  methods: {
    play(url) {
      if (!url) return;
      const rtcClient = window.ZLMRTCClient;
      if (!window.RTCPeerConnection || !rtcClient || !this.$refs.video) {
        this.emitStreamError('unsupported');
        return;
      }

      this.closeEndpoint();
      this.clearFirstFrameTimer();
      this.firstFrameTimer = setTimeout(() => {
        this.emitStreamError('first-frame-timeout');
      }, 8000);

      this.webrtcPlayer = new rtcClient.Endpoint({
        element: this.$refs.video,
        debug: true,
        zlmsdpUrl: url,
        simulecast: false,
        useCamera: false,
        audioEnable: this.hasaudio !== false,
        videoEnable: true,
        recvOnly: true,
        usedatachannel: false,
      });

      const events = rtcClient.Events || {};
      this.webrtcPlayer.on(events.WEBRTC_ICE_CANDIDATE_ERROR, (e) => {
        console.error('ICE candidate error');
        this.eventcallbacK('ICE ERROR', 'ICE candidate error');
        this.emitStreamError('ice-candidate-error', e);
      });

      this.webrtcPlayer.on(events.WEBRTC_ON_REMOTE_STREAMS, (e) => {
        this.clearFirstFrameTimer();
        console.log('WebRTC playing', e.streams);
        this.$emit('playing', this.videoUrl);
        this.eventcallbacK('playing', 'WebRTC playing');
      });

      this.webrtcPlayer.on(events.WEBRTC_ON_CONNECTION_STATE_CHANGE, (e) => {
        const state = e?.state || e?.connectionState || e;
        if (state === 'failed') {
          this.emitStreamError('connection-failed', e);
        }
      });

      this.webrtcPlayer.on(events.WEBRTC_OFFER_ANWSER_EXCHANGE_FAILED, (e) => {
        console.error('offer answer exchange failed', e);
        this.eventcallbacK('OFFER ANSWER ERROR', 'offer answer exchange failed');
        this.emitStreamError('offer-answer-error', e);
        if (e?.code === -400) {
          this.timer = setTimeout(() => {
            this.closeEndpoint();
            this.play(url);
          }, 100);
        }
      });

      this.webrtcPlayer.on(events.WEBRTC_ON_LOCAL_STREAM, () => {
        this.eventcallbacK('LOCAL STREAM', 'local stream received');
      });
    },
    pause() {
      this.clearFirstFrameTimer();
      if (this.timer) {
        clearTimeout(this.timer);
        this.timer = null;
      }
      this.closeEndpoint();
    },
    requestFullscreen() {
      this.$refs.video?.requestFullscreen?.();
    },
    closeEndpoint() {
      if (this.webrtcPlayer) {
        this.webrtcPlayer.close();
        this.webrtcPlayer = null;
      }
    },
    clearFirstFrameTimer() {
      if (this.firstFrameTimer) {
        clearTimeout(this.firstFrameTimer);
        this.firstFrameTimer = null;
      }
    },
    emitStreamError(type, detail) {
      this.clearFirstFrameTimer();
      this.$emit('stream-error', { type, detail });
    },
    eventcallbacK(type, message) {
      console.log('player event callback');
      console.log(type);
      console.log(message);
    },
  },
};
</script>

<style>
.LodingTitle {
  min-width: 70px;
}

.rtc-player {
  width: 100%;
  height: 100%;
}

.rtc-player__video {
  width: 100%;
  height: 100%;
  background-color: #000;
  object-fit: contain;
}
</style>
