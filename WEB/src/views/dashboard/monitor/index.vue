<template>
  <div
    class="monitor-dashboard"
    :class="{ 'monitor-dashboard--embedded': dashboardOverlayReleased }"
    data-testid="monitor-dashboard"
  >
    <!-- 顶部头部 -->
    <MonitorHeader
      :active-videos="activeVideos"
      :statistics="statistics"
      :today-alarm-count="todayAlarmCount"
      :dashboard-health="dashboardHealth"
      :last-updated-text="lastUpdatedText"
      @admin-entry="releaseDashboardOverlay"
    />
    
    <!-- 主体内容 -->
    <div class="monitor-content">
      <!-- 左侧导航 -->
      <MonitorSidebar
        class="monitor-devices"
        :selected-device="selectedDevice"
        @device-change="handleDeviceChange"
        @device-play="handleDevicePlay"
      />
      
      <!-- 中央视频监控区域 -->
      <div class="monitor-center">
        <VideoMonitor 
          ref="videoMonitorRef"
          :device="selectedDevice"
          :video-list="videoList"
          :alert-record-list="alarmList"
          @video-list-change="handleVideoListChange"
        />
      </div>
      
      <!-- 右侧告警信息 -->
      <AlarmPanel
        class="monitor-alarms"
        :alarm-list="alarmList"
        :today-alarm-count="todayAlarmCount"
        :dashboard-health="dashboardHealth"
        :refreshing="refreshing"
        @play-alarm="handlePlayAlarm"
        @retry="refreshDashboardData"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import MonitorHeader from './components/Header.vue'
import MonitorSidebar from './components/Sidebar.vue'
import VideoMonitor from './components/VideoMonitor.vue'
import AlarmPanel from './components/AlarmPanel.vue'
import { useDashboardData } from './useDashboardData'

defineOptions({
  name: 'MonitorDashboard'
})

const videoMonitorRef = ref<InstanceType<typeof VideoMonitor> | null>(null)
const dashboardOverlayReleased = ref(false)

// GB28181 的 gb_ch_* 仅用于播放，告警与统计接口必须使用同步后的真实设备 ID。
const selectedDevice = ref<any>(null)
const activeDeviceId = computed(() => {
  const candidate = selectedDevice.value?.device?.id ?? selectedDevice.value?.id
  const deviceId = String(candidate ?? '').trim()
  return !deviceId || deviceId.startsWith('gb_ch_') ? '' : deviceId
})

const {
  alarmList,
  dashboardHealth,
  lastUpdatedText,
  refreshDashboardData,
  refreshing,
  statistics,
  todayAlarmCount,
} = useDashboardData(activeDeviceId)

function releaseDashboardOverlay() {
  dashboardOverlayReleased.value = true
  const style = document.getElementById('monitor-dashboard-style')
  if (style) {
    document.head.removeChild(style)
  }
}

defineExpose({ releaseDashboardOverlay })

// 视频列表
const videoList = ref([
  { id: '1', url: '', name: '主视频' },
  { id: '2', url: '', name: '视频1' },
  { id: '3', url: '', name: '视频2' },
  { id: '4', url: '', name: '视频3' },
  { id: '5', url: '', name: '视频4' },
  { id: '6', url: '', name: '视频5' },
  { id: '7', url: '', name: '视频6' }
])

// 正在播放的视频列表
const activeVideos = ref<any[]>([])

/** 大屏层 z-index 为 9999，需抬高挂载在 body 上的弹层，否则确认框/提示会被挡住 */
const MONITOR_OVERLAY_Z_INDEX = 10050

// 动态添加样式，隐藏顶部导航栏、标签页和左侧菜单，让大屏覆盖整个屏幕
onMounted(() => {
  if (!document.getElementById('monitor-dashboard-style')) {
    const style = document.createElement('style')
    style.id = 'monitor-dashboard-style'
    style.textContent = `
      .ant-layout-header,
      .layout-multiple-header,
      .layout-tabs,
      .layout-footer,
      [class*="layout-header"],
      [class*="layout-multiple-header"],
      [class*="multiple-tabs"],
      [class*="layout-footer"] {
        display: none !important;
      }
      .ant-layout-sider,
      .layout-sider-wrapper,
      [class*="layout-sider"] {
        display: none !important;
      }
      .ant-layout-content,
      .layout-content {
        padding: 0 !important;
        margin: 0 !important;
        height: 100vh !important;
        overflow: hidden !important;
      }
      .ant-layout-main {
        height: 100vh !important;
        overflow: hidden !important;
        margin-left: 0 !important;
      }
      .ant-modal-root,
      .ant-modal-wrap,
      .ant-modal-mask,
      .ant-message,
      .ant-notification {
        z-index: ${MONITOR_OVERLAY_Z_INDEX} !important;
      }
    `
    document.head.appendChild(style)
  }
})

onUnmounted(() => {
  const style = document.getElementById('monitor-dashboard-style')
  if (style) {
    document.head.removeChild(style)
  }
})

// 设备切换
const handleDeviceChange = (device: any) => {
  selectedDevice.value = device
  // 这里可以加载新设备的视频流
}

// 设备播放
const handleDevicePlay = (device: any) => {
  if (videoMonitorRef.value) {
    videoMonitorRef.value.playDeviceStream(device)
  }
}

// 告警事件点击播放录像
const handlePlayAlarm = (alarm: any) => {
  if (videoMonitorRef.value) {
    videoMonitorRef.value.playAlertRecord(alarm)
  }
}

// 处理视频列表变化
const handleVideoListChange = (videos: any[]) => {
  activeVideos.value = videos
}

</script>

<style lang="less">
.monitor-dashboard {
  --dashboard-bg: #061017;
  --dashboard-bg-soft: #08151d;
  --dashboard-panel: #0a1720;
  --dashboard-panel-strong: #0e202b;
  --dashboard-border: #1d3541;
  --dashboard-border-strong: #2d5967;
  --dashboard-cyan: #26d5e4;
  --dashboard-blue: var(--dashboard-cyan);
  --dashboard-green: #3ddc97;
  --dashboard-amber: #f5b942;
  --dashboard-accent: var(--dashboard-amber);
  --dashboard-red: #ff5f6d;
  --dashboard-danger: var(--dashboard-red);
  --dashboard-text: #edf7fa;
  --dashboard-muted: #91a8b4;
  --dashboard-weak: #617a86;
  --dashboard-radius: 2px;

  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  max-height: 100vh;
  background: var(--dashboard-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--dashboard-text);
  font-size: 14px;
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  text-rendering: geometricPrecision;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  isolation: isolate;

  a {
    text-decoration: none;
    color: var(--dashboard-cyan);
  }
}

.monitor-dashboard--embedded {
  pointer-events: none;
  opacity: 0;
}

.monitor-content {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 284px minmax(0, 1fr) 328px;
  grid-template-areas: 'devices center alarms';
  overflow: hidden;
  padding: 12px 16px 16px;
  gap: 12px;
  box-sizing: border-box;
}

.monitor-center {
  grid-area: center;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.monitor-devices {
  grid-area: devices;
  min-width: 0;
  min-height: 0;
}

.monitor-alarms {
  grid-area: alarms;
  min-width: 0;
  min-height: 0;
}

@media (max-width: 1600px) {
  .monitor-content {
    grid-template-columns: 248px minmax(0, 1fr) 300px;
    gap: 10px;
    padding: 10px 12px 12px;
  }
}

@media (max-width: 1180px) {
  .monitor-content {
    grid-template-columns: minmax(220px, 26vw) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(220px, 0.72fr);
    grid-template-areas:
      'devices center'
      'alarms center';
    gap: 8px;
    padding: 8px;
  }
}

@media (max-width: 767px) {
  .monitor-content {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto auto;
    grid-template-areas:
      'center'
      'alarms'
      'devices';
    align-content: start;
    overflow-x: hidden;
    overflow-y: auto;
    padding: 8px;
    gap: 8px;
    scrollbar-width: thin;
    scrollbar-color: var(--dashboard-border-strong) transparent;
  }

  .monitor-center {
    height: min(64vh, 560px);
    min-height: 480px;
  }
}
</style>
