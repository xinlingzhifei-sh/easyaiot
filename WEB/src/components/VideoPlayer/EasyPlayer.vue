<template>
  <div :id="playerId" class="easy-player"></div>
</template>

<script>
export default {
  name: 'EasyWasmPlayer',
  emits: ['stream-error', 'playing'],
  props: {
    videoUrl: {
      type: String,
      default: '',
    },
    hasaudio: {
      type: Boolean,
      default: false,
    },
    hasAudio: {
      type: Boolean,
      default: false,
    },
    height: {
      type: [String, Number, Boolean],
      default: false,
    },
    decodeType: {
      type: String,
      default: 'auto',
    },
  },
  data() {
    return {
      easyPlayer: null,
      firstFrameTimer: null,
      firstFrameTimeoutMs: 10000,
      hasStarted: false,
      playerId: `easyplayer-${Math.random().toString(36).slice(2)}`,
    };
  },
  mounted() {
    this.$nextTick(() => {
      if (this.videoUrl) this.play(this.videoUrl);
    });
  },
  watch: {
    videoUrl(newUrl) {
      if (newUrl) {
        this.play(newUrl);
      } else {
        this.destroyPlayer();
      }
    },
    decodeType() {
      if (this.videoUrl) this.play(this.videoUrl);
    },
  },
  beforeUnmount() {
    this.destroyPlayer();
  },
  destroyed() {
    this.destroyPlayer();
  },
  methods: {
    createPlayer() {
      if (typeof window === 'undefined' || !window.WasmPlayer) {
        this.$emit('stream-error', { type: 'missing-wasm-player' });
        return null;
      }
      return new window.WasmPlayer(null, this.playerId, this.eventCallback, {
        Height: this.height,
        decodeType: this.decodeType,
        openAudio: this.hasaudio || this.hasAudio ? 1 : 0,
      });
    },
    destroyPlayer() {
      this.clearFirstFrameTimer();
      this.hasStarted = false;
      if (this.easyPlayer && typeof this.easyPlayer.destroy === 'function') {
        this.easyPlayer.destroy();
      }
      this.easyPlayer = null;
    },
    play(url) {
      const target = typeof url === 'string' ? url.trim() : '';
      if (!target) return;
      this.destroyPlayer();
      const player = this.createPlayer();
      if (!player) return;
      this.easyPlayer = player;
      this.startFirstFrameTimer();
      try {
        this.easyPlayer.play(target, 1);
      } catch (error) {
        this.emitStreamError('play-exception', error);
      }
    },
    pause() {
      this.destroyPlayer();
    },
    startFirstFrameTimer() {
      this.clearFirstFrameTimer();
      this.hasStarted = false;
      this.firstFrameTimer = window.setTimeout(() => {
        this.emitStreamError('first-frame-timeout');
      }, this.firstFrameTimeoutMs);
    },
    clearFirstFrameTimer() {
      if (this.firstFrameTimer) {
        window.clearTimeout(this.firstFrameTimer);
        this.firstFrameTimer = null;
      }
    },
    emitStreamError(type, detail) {
      this.clearFirstFrameTimer();
      this.hasStarted = false;
      this.$emit('stream-error', { type, detail });
    },
    markPlaying() {
      if (this.hasStarted) return;
      this.hasStarted = true;
      this.clearFirstFrameTimer();
      this.$emit('playing');
    },
    isPlayingEvent(type) {
      return [
        'play',
        'playing',
        'loadedmetadata',
        'loadeddata',
        'canplay',
        'media_info',
        'mediainfo',
        'video_info',
        'videoinfo',
        'frame',
        'render',
        'rendered',
      ].includes(type);
    },
    eventCallback(type, message) {
      const eventType = String(type ?? '').toLowerCase();
      if (eventType === 'error' || eventType === 'timeout') {
        this.emitStreamError(eventType, message);
        return;
      }
      if (this.isPlayingEvent(eventType)) {
        this.markPlaying();
      }
    },
  },
};
</script>

<style>
.easy-player {
  width: 100%;
  height: 100%;
  background: #000c17;
}

.iconqingxiLOGO {
  display: none !important;
}
</style>
