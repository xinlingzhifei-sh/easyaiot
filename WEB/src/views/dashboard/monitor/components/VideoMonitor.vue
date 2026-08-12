<template>
  <div class="video-monitor" :class="{ 'preset-panel-open': presetPanelOpen }" data-testid="monitor-video">
    <div class="monitor-header" :class="{ 'panel-open': presetPanelOpen }">
      <div class="header-title">实时画面</div>
      <div class="enable-ai-wrap" data-testid="monitor-ai-toggle">
        <a-checkbox v-model:checked="enableAi">启用 AI</a-checkbox>
      </div>
      <div class="header-time">{{ currentTime }}</div>
      <div class="header-location">{{ currentLocation }}</div>
      <div class="header-toolbar">
        <!-- 分屏切换 -->
        <div class="split-toolbar" data-testid="monitor-split-toolbar">
          <div
            v-for="layout in splitLayouts"
            :key="layout.value"
            :class="['split-btn', { active: currentLayout === layout.value }]"
            :data-testid="`monitor-split-${layout.value}`"
            :title="layout.label"
            @click="switchLayout(layout.value)"
          >
            {{ layout.label }}
          </div>
        </div>
        <!-- 布局方案入口 -->
        <div
          :class="['toolbar-trigger', 'layout-preset-trigger', { open: presetPanelOpen, 'has-active': !!activePresetId }]"
          @click="presetPanelOpen = !presetPanelOpen"
        >
          <Icon icon="ant-design:layout-outlined" :size="15" />
          <span class="trigger-label">布局方案</span>
          <span v-if="activePresetSummary" class="trigger-badge">{{ activePresetSummary }}</span>
          <Icon :icon="presetPanelOpen ? 'ant-design:up-outlined' : 'ant-design:down-outlined'" :size="12" />
        </div>
      </div>
      <LayoutPresetPanel
        :open="presetPanelOpen"
        :presets="layoutPresets"
        :active-preset-id="activePresetId"
        :current-layout="currentLayout"
        :current-camera-count="currentCameraCount"
        :can-save-current="canSaveCurrentLayout"
        @close="presetPanelOpen = false"
        @apply="handleApplyPreset"
        @save="handleSavePreset"
        @delete="handleDeletePreset"
      />
    </div>

    <div v-if="presetPanelOpen" class="preset-panel-backdrop" @click="presetPanelOpen = false"></div>

    <div class="monitor-content" :class="`layout-${currentLayout}`">
      <!-- 根据当前布局渲染视频窗口 -->
      <div
        v-for="(video, index) in displayVideos"
        :key="`slot-${index}`"
        :class="[
          'video-window',
          getVideoClass(index),
          {
            'drag-over': dragOverIndex === index,
            'is-dragging': dragSourceIndex === index,
          },
        ]"
        :data-testid="`monitor-video-window-${index}`"
        :style="getVideoStyle(index)"
        @click="handleVideoClick(index)"
        @contextmenu.prevent="removeVideoAtIndex(index)"
        @dragover.prevent="handleDragOver(index)"
        @dragleave="handleDragLeave(index)"
        @drop.prevent="handleDrop(index)"
      >
        <div class="video-container">
          <div v-if="!video.url" class="video-placeholder">
            <img src="@/assets/images/bigscreen/camera-icon.svg" alt="摄像头" class="camera-icon" />
            <div class="placeholder-text">{{ displayVideoName(video, index) }}</div>
          </div>
          <Jessibuca
            v-else
            :key="`player-${index}-${video.deviceId || video.url}`"
            :playUrl="video.url"
            :hasAudio="false"
            :playerEngine="video.playerEngine || ''"
            :videoCodec="video.videoCodec || ''"
            :fill-video="true"
            :multi-view="getMaxVideoCount(currentLayout) > 1"
            :ai-with-fallback="!!video.fallbackUrl"
            :ref="el => setVideoRef(el, index)"
            class="video-player"
            @playing="handleVideoPlaying(index, $event)"
            @stream-error="handleVideoStreamError(index)"
          />
          <div
            class="drag-zone"
            draggable="true"
            title="拖拽到目标通道交换位置"
            @dragstart="handleDragStart(index, $event)"
            @dragend="handleDragEnd"
          />
          <button
            v-if="hasVideoContent(video)"
            type="button"
            class="video-close-btn"
            title="移除通道"
            @click.stop="removeVideoAtIndex(index)"
          >
            <Icon icon="ant-design:close-outlined" :size="14" />
          </button>
          <div
            v-if="hasVideoContent(video)"
            :class="['ai-status-tag', getAiStatusClass(video)]"
          >
            {{ getAiStatusText(video) }}
          </div>
          <div class="video-label" :title="displayVideoName(video, index)">
            {{ displayVideoName(video, index) }}
          </div>
          <div v-if="index === activeVideoIndex" class="video-active-indicator"></div>
        </div>
      </div>
    </div>
    
    <!-- 活跃事件列表 -->
    <div class="alert-record-list">
      <div class="alert-record-header">
        <span class="header-title">活跃事件</span>
        <span class="header-count">共 {{ alertRecordList.length }} 条</span>
      </div>
      <div class="alert-record-wrapper">
        <!-- 左滑动按钮 -->
        <div
          v-if="canScrollLeft"
          class="scroll-btn scroll-btn-left"
          @click="scrollLeft"
        >
          <Icon icon="ant-design:left-outlined" :size="20" />
        </div>
        <!-- 右滑动按钮 -->
        <div
          v-if="canScrollRight"
          class="scroll-btn scroll-btn-right"
          @click="scrollRight"
        >
          <Icon icon="ant-design:right-outlined" :size="20" />
        </div>
        <div
          ref="scrollContainerRef"
          class="alert-record-scroll"
          @scroll="handleScroll"
        >
          <div
            v-for="(record, index) in alertRecordList"
            :key="record.id || index"
            class="alert-record-item"
            :data-testid="`monitor-alert-record-${index}`"
            @click="handleRecordClick(record)"
          >
            <div class="record-info">
              <div class="record-title">{{ formatAlertListTitle(record) }}</div>
              <div class="record-meta">
                <span class="record-device">{{ record.device_name || record.device_id || '未知设备' }}</span>
                <span class="record-time">{{ formatTime(record.time) }}</span>
                <Icon icon="ant-design:play-circle-outlined" :size="20" class="play-icon" />
              </div>
            </div>
          </div>
          <div v-if="alertRecordList.length === 0" class="empty-records">
            <Icon icon="ant-design:inbox-outlined" :size="32" />
            <span>暂无活跃事件</span>
          </div>
        </div>
      </div>
    </div>
    
    <div class="boxfoot"></div>
    
    <!-- 视频播放器弹窗 -->
    <DialogPlayer @register="registerPlayerModal" />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted, onUnmounted, onActivated, nextTick } from 'vue'
import { Icon } from '@/components/Icon'
import { Checkbox as ACheckbox } from 'ant-design-vue'
import { playAlertRecordInModal } from '@/utils/alertRecordPlayback'
import { useMessage } from '@/hooks/web/useMessage'
import Jessibuca from '@/components/Player/module/jessibuca.vue'
import DialogPlayer from '@/components/VideoPlayer/DialogPlayer.vue'
import LayoutPresetPanel from './LayoutPresetPanel.vue'
import { useModal } from '@/components/Modal'
import { formatAlertListTitle, isSnapAlertTask } from '@/views/alert/alertDisplay'
import { formatCameraDeviceLabel, formatCameraShortName, isGb28181Device } from '@/views/camera/utils/deviceLabel'
import {
  AI_PLAY_FALLBACK_MS,
  loadGbChannelSyncedDevice,
  pickDirectPlayUrls,
  resolveGbChannelPlayUrls,
  schedulePendingAiStreamUpgrade,
} from '@/views/camera/utils/devicePlay'
import { parseGbChannelKey } from '@/views/camera/utils/gb28181Tree'
import type { WvpPlaySourceOption } from '@/views/camera/utils/livePlayer'
import type { MonitorTreeDeviceNode } from '@/api/device/camera'
import {
  createAlgorithmTask,
  listAlgorithmTasks,
  startAlgorithmTask,
  stopAlgorithmTask,
} from '@/api/device/algorithm_task'
import { getModelPage } from '@/api/device/model'
import {
  extractDashboardGuardErrorMessage,
  startDashboardGuardTask,
  stopDashboardGuardTask,
  type DashboardGuardScope,
  type DashboardGuardTaskApi,
} from '../dashboardGuardTask'
import {
  MAX_MONITOR_LAYOUT_PRESETS,
  loadMonitorLayoutStorage,
  saveMonitorLayoutStorage,
  serializeDeviceSnapshot,
  type MonitorLayoutPreset,
  type MonitorLayoutSlot,
} from '../utils/monitorLayoutStorage'

/** 与 monitor/index.vue 中 MONITOR_OVERLAY_Z_INDEX 保持一致 */
const MONITOR_OVERLAY_Z_INDEX = 10050

defineOptions({
  name: 'VideoMonitor'
})

const props = defineProps<{
  device?: any
  videoList?: any[]
  alertRecordList?: any[]
}>()

const emit = defineEmits<{
  'video-list-change': [videos: any[]]
}>()

const { createMessage, createConfirm } = useMessage()

// 播放器弹窗
const [registerPlayerModal, { openModal: openPlayerModal, closeModal: closePlayerModal, setModalProps: setPlayerModalProps }] = useModal()

const currentTime = ref('')
const activeVideoIndex = ref(0)
const dragSourceIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const currentLayout = ref('1')
const AI_TOGGLE_STORAGE_KEY = 'yfeieye.dashboard.monitor.enableAi'
const VIDEO_STATE_STORAGE_KEY = 'yfeieye.dashboard.monitor.videoState'

type PersistedDashboardVideoSlot = {
  index: number
  id: string
  name: string
  url: string
  deviceId?: string
  location?: string
  device?: MonitorTreeDeviceNode
  playerEngine?: string
  videoCodec?: string
}

type PersistedDashboardVideoState = {
  layout: string
  activeVideoIndex: number
  videos: PersistedDashboardVideoSlot[]
}

type DashboardAiStatus = 'empty' | 'original' | 'ai' | 'fallback' | 'unavailable'

type DashboardVideoSlot = {
  id: string
  name: string
  url: string
  aiStatus?: DashboardAiStatus
  deviceId?: string
  location?: string
  device?: MonitorTreeDeviceNode
  playerEngine?: string
  videoCodec?: string
  fallbackUrl?: string | null
  playSources?: WvpPlaySourceOption[] | null
  pendingRestore?: boolean
}

function readPersistedEnableAi() {
  if (typeof window === 'undefined') return false
  try {
    return window.sessionStorage.getItem(AI_TOGGLE_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function persistEnableAi(checked: boolean) {
  if (typeof window === 'undefined') return
  try {
    window.sessionStorage.setItem(AI_TOGGLE_STORAGE_KEY, String(checked))
  } catch {
    // Keep the toggle usable when storage is unavailable.
  }
}

function readPersistedVideoState(): PersistedDashboardVideoState | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(VIDEO_STATE_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PersistedDashboardVideoState
    return Array.isArray(parsed?.videos) ? parsed : null
  } catch {
    return null
  }
}

const enableAi = ref(readPersistedEnableAi())
const videoRefs = ref<(InstanceType<typeof Jessibuca> | null)[]>([])
const alertRecordList = computed(() => props.alertRecordList ?? [])
const scrollContainerRef = ref<HTMLElement | null>(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const internalVideoList = ref<DashboardVideoSlot[]>([])

const dashboardGuardApi: DashboardGuardTaskApi = {
  listAlgorithmTasks: (params) => listAlgorithmTasks(params, { errorMessageMode: 'none' }),
  listAvailableModels: () => getModelPage(
    { pageNo: 1, pageSize: 1000 },
    { errorMessageMode: 'none' },
  ),
  createAlgorithmTask: createAlgorithmTask as DashboardGuardTaskApi['createAlgorithmTask'],
  startAlgorithmTask: (taskId) => startAlgorithmTask(taskId, { errorMessageMode: 'none' }),
  stopAlgorithmTask: (taskId) => stopAlgorithmTask(taskId, { errorMessageMode: 'none' }),
}

function isKnownLayout(layout: string | undefined) {
  return !!layout && splitLayouts.some(item => item.value === layout)
}

function ensureVideoSlots(maxCount: number) {
  while (internalVideoList.value.length < maxCount) {
    internalVideoList.value.push({
      id: `placeholder-${internalVideoList.value.length}`,
      url: '',
      name: `视频${internalVideoList.value.length + 1}`,
      aiStatus: 'empty',
    })
  }
}

function getDashboardAiDeviceId(device?: MonitorTreeDeviceNode | null) {
  const id = String(device?.id ?? '').trim()
  if (!id || id.startsWith('gb_ch_')) return ''
  return id
}

function collectDashboardAiDevices(extraDevice?: MonitorTreeDeviceNode | null): MonitorTreeDeviceNode[] {
  const byId = new Map<string, MonitorTreeDeviceNode>()
  const addDevice = (device?: MonitorTreeDeviceNode | null) => {
    const id = getDashboardAiDeviceId(device)
    if (!id || byId.has(id)) return
    byId.set(id, device as MonitorTreeDeviceNode)
  }

  internalVideoList.value.forEach((slot) => {
    const device = slot?.device as MonitorTreeDeviceNode | undefined
    if (device) {
      addDevice(device)
      return
    }
    const deviceId = String(slot?.deviceId ?? '').trim()
    if (!deviceId || deviceId.startsWith('gb_ch_')) return
    addDevice({ id: deviceId, name: slot?.name } as MonitorTreeDeviceNode)
  })

  addDevice(extraDevice)
  return [...byId.values()]
}

function buildDashboardAiScope(devices: MonitorTreeDeviceNode[]): DashboardGuardScope | null {
  const deviceIds = [...new Set(devices.map(getDashboardAiDeviceId).filter(Boolean))]
  if (!deviceIds.length) return null
  const sortedIds = [...deviceIds].sort()
  const firstDeviceName = formatCameraDeviceLabel(devices[0]) || devices[0]?.name || sortedIds[0]
  return {
    key: `monitor-ai:${sortedIds.join(',')}`,
    label: deviceIds.length === 1 ? firstDeviceName : `当前播放 ${deviceIds.length} 路`,
    deviceIds,
  }
}

async function ensureDashboardAiRecognitionForDevices(devices: MonitorTreeDeviceNode[]) {
  const scope = buildDashboardAiScope(devices)
  if (!scope) return true
  try {
    await startDashboardGuardTask({ scope, api: dashboardGuardApi })
    return true
  } catch (error) {
    createMessage.error(`AI 识别启动失败：${extractDashboardGuardErrorMessage(error)}`)
    return false
  }
}

async function ensureDashboardAiRecognitionForVisibleDevices() {
  return ensureDashboardAiRecognitionForDevices(collectDashboardAiDevices())
}

async function ensureGbDashboardAiRecognition(
  gb: { sipDeviceId: string; channelId: string },
  fallbackDevice?: MonitorTreeDeviceNode | null,
) {
  const aiDevice = await loadGbChannelSyncedDevice(gb.sipDeviceId, gb.channelId, fallbackDevice)
  if (!getDashboardAiDeviceId(aiDevice)) {
    createMessage.warning('国标通道尚未同步到设备库，无法启动 AI 识别；请先同步该通道')
    return { ready: false, device: fallbackDevice ?? null }
  }
  const ready = await ensureDashboardAiRecognitionForDevices(collectDashboardAiDevices(aiDevice))
  return { ready, device: aiDevice }
}

async function stopDashboardAiRecognitionForVisibleDevices() {
  const scope = buildDashboardAiScope(collectDashboardAiDevices())
  if (!scope) return
  try {
    await stopDashboardGuardTask({ scope, api: dashboardGuardApi })
  } catch (error) {
    console.warn('停止首页 AI 识别任务失败:', error)
  }
}

function persistDashboardVideoState() {
  if (typeof window === 'undefined') return

  const videos = internalVideoList.value
    .map<PersistedDashboardVideoSlot | null>((video, index) => {
      if (!video?.url || !video.deviceId) return null
      return {
        index,
        id: String(video.id || `video-${index}`),
        name: String(video.name || `视频${index + 1}`),
        url: String(video.url),
        deviceId: String(video.deviceId),
        location: video.location || '',
        device: video.device,
        playerEngine: video.playerEngine || '',
        videoCodec: video.videoCodec || '',
      } satisfies PersistedDashboardVideoSlot
    })
    .filter((video): video is PersistedDashboardVideoSlot => video !== null)

  try {
    if (!videos.length) {
      window.sessionStorage.removeItem(VIDEO_STATE_STORAGE_KEY)
      return
    }

    const state: PersistedDashboardVideoState = {
      layout: currentLayout.value,
      activeVideoIndex: activeVideoIndex.value,
      videos,
    }
    window.sessionStorage.setItem(VIDEO_STATE_STORAGE_KEY, JSON.stringify(state))
  } catch {
    // Keep playback working even when storage is full or disabled.
  }
}

async function restorePersistedVideoState() {
  const state = readPersistedVideoState()
  if (!state?.videos.length) return

  if (isKnownLayout(state.layout)) {
    currentLayout.value = state.layout
  }

  const maxCount = getMaxVideoCount(currentLayout.value)
  ensureVideoSlots(maxCount)

  if (Number.isInteger(state.activeVideoIndex)) {
    activeVideoIndex.value = Math.max(0, Math.min(state.activeVideoIndex, maxCount - 1))
  }

  const reloadIndexes: number[] = []
  state.videos.forEach((video) => {
    const index = Number(video.index)
    if (!Number.isInteger(index) || index < 0 || index >= maxCount) return
    if (!video.url || !video.deviceId) return

    internalVideoList.value[index] = {
      id: video.id,
      url: video.url,
      name: video.name,
      deviceId: video.deviceId,
      location: video.location || '',
      device: video.device,
      playerEngine: video.playerEngine || '',
      videoCodec: video.videoCodec || '',
      aiStatus: 'original',
    }
    reloadIndexes.push(index)
  })

  if (enableAi.value) {
    const recognitionReady = await ensureDashboardAiRecognitionForVisibleDevices()
    if (!recognitionReady) {
      enableAi.value = false
    }
  }

  const tasks = reloadIndexes.map((index) =>
    reloadVideoAtIndex(index).catch((error) => {
      console.warn('恢复首页视频失败:', error)
    }),
  )

  await Promise.all(tasks)
  persistDashboardVideoState()
}

let lastResumeAttemptAt = 0

async function resumeDashboardVideosAfterRouteReturn() {
  const now = Date.now()
  if (now - lastResumeAttemptAt < 500) return
  lastResumeAttemptAt = now

  await nextTick()
  const tasks: Promise<void>[] = []
  internalVideoList.value.forEach((slot, index) => {
    if (slot?.url && slot.deviceId) {
      tasks.push(reloadVideoAtIndex(index).catch((error) => {
        console.warn('重连首页视频失败:', error)
      }))
    }
  })

  if (tasks.length) {
    await Promise.all(tasks)
    persistDashboardVideoState()
    return
  }

  await restorePersistedVideoState()
}

const skipDefaultVideoInit = ref(false)
const layoutPresets = ref<Record<number, MonitorLayoutPreset>>({})
const activePresetId = ref<number | null>(null)
const isRestoringLayout = ref(false)
const presetPanelOpen = ref(false)

function displayVideoName(video: { name?: string }, index: number) {
  const shortName = formatCameraShortName(video?.name)
  return shortName || `视频${index + 1}`
}

function getPresetDisplayName(preset: MonitorLayoutPreset | undefined, id: number) {
  if (!preset) return `方案 ${id}`
  return preset.name?.trim() || `方案 ${id}`
}

const currentCameraCount = computed(() =>
  internalVideoList.value.filter((v) => v?.deviceId).length,
)

const canSaveCurrentLayout = computed(() => currentCameraCount.value > 0)

const activePresetSummary = computed(() => {
  if (!activePresetId.value) return ''
  const preset = layoutPresets.value[activePresetId.value]
  if (!preset) return `方案 ${activePresetId.value}`
  const count = preset.slots.filter((s) => s.deviceId).length
  return `${getPresetDisplayName(preset, activePresetId.value)} · ${count}路`
})

function initLayoutPresetsFromStorage() {
  const storage = loadMonitorLayoutStorage()
  const presets: Record<number, MonitorLayoutPreset> = {}
  for (const [key, preset] of Object.entries(storage.presets)) {
    const id = Number(key)
    if (id >= 1 && id <= MAX_MONITOR_LAYOUT_PRESETS) {
      presets[id] = preset
    }
  }
  layoutPresets.value = presets
  activePresetId.value = storage.activePresetId
}

function persistLayoutPresets() {
  const presets: Record<string, MonitorLayoutPreset> = {}
  for (const [key, preset] of Object.entries(layoutPresets.value)) {
    presets[String(key)] = preset
  }
  saveMonitorLayoutStorage({
    presets,
    activePresetId: activePresetId.value,
  })
}

function saveToLayoutPreset(presetId: number, options?: { keepName?: boolean }) {
  if (!canSaveCurrentLayout.value) {
    createMessage.warning('当前没有已选摄像头，无法保存布局')
    return false
  }

  const existing = layoutPresets.value[presetId]
  const preset: MonitorLayoutPreset = {
    id: presetId,
    name: options?.keepName !== false && existing?.name ? existing.name : undefined,
    layout: currentLayout.value,
    enableAi: enableAi.value,
    slots: buildCurrentLayoutSlots(),
    updatedAt: Date.now(),
  }
  layoutPresets.value = { ...layoutPresets.value, [presetId]: preset }
  activePresetId.value = presetId
  persistLayoutPresets()
  createMessage.success(`已保存到${getPresetDisplayName(preset, presetId)}`)
  return true
}

function buildCurrentLayoutSlots(): MonitorLayoutSlot[] {
  const maxCount = getMaxVideoCount(currentLayout.value)
  const slots: MonitorLayoutSlot[] = []
  for (let i = 0; i < maxCount; i++) {
    const video = internalVideoList.value[i]
    if (video?.deviceId) {
      slots.push({
        deviceId: video.deviceId,
        name: video.name || `视频${i + 1}`,
        location: video.location || '',
        device: serializeDeviceSnapshot(video.device),
      })
    } else {
      slots.push({
        deviceId: '',
        name: video?.name || `视频${i + 1}`,
      })
    }
  }
  return slots
}

function applyPresetStructure(preset: MonitorLayoutPreset) {
  skipDefaultVideoInit.value = true
  currentLayout.value = normalizeSplitLayout(preset.layout)
  enableAi.value = !!preset.enableAi
  activeVideoIndex.value = 0

  const maxCount = getMaxVideoCount(currentLayout.value)
  internalVideoList.value = []
  for (let i = 0; i < maxCount; i++) {
    const saved = preset.slots[i]
    if (saved?.deviceId) {
      internalVideoList.value.push({
        id: `video-${saved.deviceId}-${i}`,
        url: '',
        name: saved.name,
        deviceId: saved.deviceId,
        location: saved.location || '',
        device: saved.device,
        pendingRestore: true,
      })
    } else {
      internalVideoList.value.push({
        id: `placeholder-${i}`,
        url: '',
        name: saved?.name || `视频${i + 1}`,
      })
    }
  }
}

async function playSavedSlot(index: number, saved: MonitorLayoutSlot) {
  const playId = saved.deviceId
  if (!playId) return

  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId)
    if (!gb) return
    let playDevice: MonitorTreeDeviceNode | null | undefined = saved.device
    if (enableAi.value) {
      const recognition = await ensureGbDashboardAiRecognition(gb, saved.device)
      playDevice = recognition.device ?? saved.device
      if (!recognition.ready) {
        enableAi.value = false
      }
    }
    const { url, fallbackUrl, preferAi, playerEngine, videoCodec, playSources, pendingAiUrl } = await resolveGbChannelPlayUrls(
      gb.sipDeviceId,
      gb.channelId,
      { enableAi: enableAi.value, synced: playDevice },
    )
    if (!url) {
      createMessage.warn(`方案恢复失败：${saved.name}`)
      return
    }
    await startPlayAtScreen(index, {
      id: `video-${playId}-${index}`,
      name: saved.name,
      url,
      deviceId: playId,
      location: saved.location,
      device: playDevice ?? saved.device,
      fallbackUrl,
      preferAi,
      playerEngine,
      videoCodec,
      playSources,
      pendingAiUrl,
    })
    return
  }

  const dev = saved.device
  if (!dev) {
    createMessage.warn(`方案恢复失败：${saved.name}（缺少设备信息）`)
    return
  }
  if (enableAi.value) {
    const recognitionReady = await ensureDashboardAiRecognitionForDevices(collectDashboardAiDevices(dev))
    if (!recognitionReady) {
      enableAi.value = false
    }
  }
  const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolvePlayUrlsForDevice(dev)
  if (!url) {
    createMessage.warn(`方案恢复失败：${saved.name}`)
    return
  }
  await startPlayAtScreen(index, {
    id: `video-${playId}-${index}`,
    name: saved.name,
    url,
    deviceId: playId,
    location: saved.location,
    device: dev,
    fallbackUrl,
    preferAi,
    pendingAiUrl,
  })
}

async function restorePendingVideos() {
  if (isRestoringLayout.value) return
  isRestoringLayout.value = true
  try {
    const tasks: Promise<void>[] = []
    internalVideoList.value.forEach((slot, idx) => {
      if (!slot?.pendingRestore || !slot.deviceId) return
      tasks.push(
        playSavedSlot(idx, {
          deviceId: slot.deviceId,
          name: slot.name,
          location: slot.location,
          device: slot.device,
        }).finally(() => {
          slot.pendingRestore = false
        }),
      )
    })
    await Promise.all(tasks)
  } finally {
    isRestoringLayout.value = false
  }
}

async function activateLayoutPreset(presetId: number) {
  const preset = layoutPresets.value[presetId]
  if (!preset) return

  aiFallbackTimers.forEach((id) => window.clearTimeout(id))
  aiFallbackTimers.clear()

  videoRefs.value.forEach((ref) => {
    if (ref) {
      ref._unmounting = true
    }
  })
  videoRefs.value = []

  activePresetId.value = presetId
  persistLayoutPresets()
  applyPresetStructure(preset)
  await nextTick()
  await restorePendingVideos()
}

async function handleApplyPreset(presetId: number) {
  await activateLayoutPreset(presetId)
  presetPanelOpen.value = false
}

function handleSavePreset(presetId: number) {
  saveToLayoutPreset(presetId)
}

function handleDeletePreset(presetId: number) {
  const preset = layoutPresets.value[presetId]
  if (!preset) return
  const label = getPresetDisplayName(preset, presetId)
  createConfirm({
    iconType: 'warning',
    title: '删除布局方案',
    content: `确定删除「${label}」吗？删除后无法恢复。`,
    zIndex: MONITOR_OVERLAY_Z_INDEX,
    onOk: () => {
      const next = { ...layoutPresets.value }
      delete next[presetId]
      layoutPresets.value = next
      if (activePresetId.value === presetId) {
        activePresetId.value = null
      }
      persistLayoutPresets()
      createMessage.success('已删除布局方案')
    },
  })
}

initLayoutPresetsFromStorage()

// 防重复提示：记录最近提示的时间和内容
let lastVideoErrorTime = 0
let lastVideoErrorMsg = ''

// 获取录像播放地址（参考录像空间的处理方式）
const getVideoUrl = (videoUrl: string): string => {
  if (!videoUrl) return ''
  // 如果是完整URL，直接返回
  if (videoUrl.startsWith('http://') || videoUrl.startsWith('https://')) {
    return videoUrl
  }
  // 如果是相对路径（以/api/v1/buckets开头），添加前端启动地址前缀
  if (videoUrl.startsWith('/api/v1/buckets')) {
    return `${window.location.origin}${videoUrl}`
  }
  // 其他相对路径，拼接API前缀
  if (videoUrl.startsWith('/')) {
    return `${import.meta.env.VITE_GLOB_API_URL || ''}${videoUrl}`
  }
  // 其他情况直接返回
  return videoUrl
}
void getVideoUrl

// 防重复提示函数：3秒内相同错误只提示一次
function showVideoErrorOnce(message: string) {
  const now = Date.now()
  // 如果3秒内提示过相同内容，则不再提示
  if (now - lastVideoErrorTime < 3000 && lastVideoErrorMsg === message) {
    return
  }
  lastVideoErrorTime = now
  lastVideoErrorMsg = message
  createMessage.warn(message)
}

// 分屏布局配置
const splitLayouts = [
  { value: '1', label: '1分屏' },
  { value: '4', label: '4分屏' },
  { value: '9', label: '9分屏' },
  { value: '16', label: '16分屏' }
]

function normalizeSplitLayout(layout: string) {
  if (splitLayouts.some((item) => item.value === layout)) return layout
  const count = parseInt(layout) || 1
  if (count <= 1) return '1'
  if (count <= 4) return '4'
  if (count <= 9) return '9'
  return '16'
}

// 设置视频引用
const setVideoRef = (el: any, index: number) => {
  if (el) {
    videoRefs.value[index] = el
  }
}

// 获取当前布局需要的最大视频数量
const getMaxVideoCount = (layout: string) => {
  const count = parseInt(layout)
  return isNaN(count) ? 1 : count
}

if (activePresetId.value && layoutPresets.value[activePresetId.value]) {
  applyPresetStructure(layoutPresets.value[activePresetId.value])
}

// 获取视频列表（填充到需要的数量）
const videoListWithPlaceholder = computed(() => {
  // 合并内部列表和props列表
  const baseList = props.videoList || []
  const maxCount = getMaxVideoCount(currentLayout.value)
  
  // 初始化内部列表（如果为空且未从布局方案恢复）
  if (!skipDefaultVideoInit.value && internalVideoList.value.length === 0 && baseList.length > 0) {
    internalVideoList.value = baseList.map((v, i) => ({
      ...v,
      id: v.id || `video-${i}`,
      url: v.url || '',
      name: v.name || `视频${i + 1}`,
      aiStatus: v.url ? (v.aiStatus || 'original') : 'empty',
    }))
  }
  
  // 确保内部列表长度足够
  ensureVideoSlots(maxCount)
  
  return internalVideoList.value.slice(0, maxCount)
})

// 显示的视频列表
const displayVideos = computed(() => {
  return videoListWithPlaceholder.value
})

// 获取正在播放的视频列表（有URL的视频）
const activeVideos = computed(() => {
  return internalVideoList.value.filter(video => video && video.url && video.url.trim() !== '')
})

// 获取当前应该显示的摄像头短名（不含目录路径与类型前缀）
const currentLocation = computed(() => {
  if (activeVideos.value.length > 0) {
    const shortName = formatCameraShortName(activeVideos.value[0].name)
    if (shortName) return shortName
  }
  const deviceShortName = formatCameraShortName(props.device?.name || props.device?.device)
  if (deviceShortName) return deviceShortName
  return '未选择设备'
})

const aiFallbackTimers = new Map<number, number>()
const aiFallbackPlayingUrls = new Map<number, string>()

function clearAiFallbackTimer(screenIdx: number) {
  const timerId = aiFallbackTimers.get(screenIdx)
  if (timerId != null) {
    window.clearTimeout(timerId)
    aiFallbackTimers.delete(screenIdx)
  }
}

function handleVideoPlaying(index: number, playUrl?: string) {
  const slot = internalVideoList.value[index]
  const currentUrl = slot?.url?.trim()
  const reportedUrl = playUrl?.trim()
  if (!currentUrl || (reportedUrl && reportedUrl !== currentUrl)) return
  aiFallbackPlayingUrls.set(index, currentUrl)
  clearAiFallbackTimer(index)
}

// 切换布局
const switchLayout = (layout: string) => {
  currentLayout.value = normalizeSplitLayout(layout)
  activeVideoIndex.value = 0
}

// 获取视频窗口的类名
const getVideoClass = (index: number) => {
  const classes: string[] = []

  if (index === activeVideoIndex.value) {
    classes.push('active')
  }

  return classes.join(' ')
}

// 获取视频窗口的样式（用于特殊布局）
const getVideoStyle = (index: number) => {
  const layout = currentLayout.value

  if (layout === '6') {
    if (index === 0) {
      return {
        gridColumn: '1 / 3',
        gridRow: '1 / 3'
      }
    }

    const pos = index - 1
    if (pos === 0) {
      return {
        gridColumn: '3',
        gridRow: '1'
      }
    }
    if (pos === 1) {
      return {
        gridColumn: '3',
        gridRow: '2'
      }
    }
    return {
      gridColumn: `${pos - 1}`,
      gridRow: '3'
    }
  }

  if (layout === '8') {
    if (index === 0) {
      return {
        gridColumn: '1 / 4',
        gridRow: '1 / 3'
      }
    }
    if (index < 4) {
      const pos = index - 1
      return {
        gridColumn: '4',
        gridRow: String(pos + 1)
      }
    }
    const pos = index - 4
    return {
      gridColumn: `${pos + 1}`,
      gridRow: '3'
    }
  }

  return {}
}

// 处理视频点击
const handleVideoClick = (index: number) => {
  activeVideoIndex.value = index
  persistDashboardVideoState()
}

function hasVideoContent(video: { url?: string } | null | undefined) {
  return !!(video?.url && video.url.trim())
}

function resolveAiStatus(preferAi?: boolean): DashboardAiStatus {
  if (preferAi) return 'ai'
  return enableAi.value ? 'unavailable' : 'original'
}

function getAiStatus(video: DashboardVideoSlot | null | undefined): DashboardAiStatus {
  if (!hasVideoContent(video)) return 'empty'
  return video?.aiStatus || 'original'
}

function getAiStatusText(video: DashboardVideoSlot | null | undefined) {
  const textMap: Record<DashboardAiStatus, string> = {
    empty: '',
    original: '原始',
    ai: 'AI',
    fallback: '回退',
    unavailable: '无 AI 流',
  }
  return textMap[getAiStatus(video)]
}

function getAiStatusClass(video: DashboardVideoSlot | null | undefined) {
  return `ai-status-tag--${getAiStatus(video)}`
}

function createPlaceholderSlot(index: number) {
  return {
    id: `placeholder-${index}`,
    url: '',
    name: `视频${index + 1}`,
    aiStatus: 'empty' as DashboardAiStatus,
  }
}

function destroyPlayerAtIndex(index: number) {
  const jessibucaInstance = videoRefs.value[index]
  if (!jessibucaInstance) return
  try {
    if (typeof jessibucaInstance.destroy === 'function' && jessibucaInstance.jessibuca) {
      jessibucaInstance.destroy()
    }
  } catch (error) {
    console.warn('销毁播放器实例时出错:', error)
  }
  videoRefs.value[index] = null
}

function removeVideoAtIndex(index: number) {
  if (!hasVideoContent(internalVideoList.value[index])) return

  clearAiFallbackTimer(index)
  destroyPlayerAtIndex(index)
  internalVideoList.value[index] = createPlaceholderSlot(index)
  persistDashboardVideoState()
  createMessage.success('已移除视频流')
}

function handleDragStart(index: number, event: DragEvent) {
  dragSourceIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function handleDragEnd() {
  dragSourceIndex.value = null
  dragOverIndex.value = null
}

function handleDragOver(index: number) {
  if (dragSourceIndex.value === null || dragSourceIndex.value === index) return
  dragOverIndex.value = index
}

function handleDragLeave(index: number) {
  if (dragOverIndex.value === index) {
    dragOverIndex.value = null
  }
}

async function swapVideoChannels(fromIndex: number, toIndex: number) {
  if (fromIndex === toIndex) return

  clearAiFallbackTimer(fromIndex)
  clearAiFallbackTimer(toIndex)

  const fromSlot = { ...internalVideoList.value[fromIndex] }
  const toSlot = { ...internalVideoList.value[toIndex] }

  internalVideoList.value[fromIndex] = hasVideoContent(toSlot)
    ? { ...toSlot, id: `video-${toSlot.deviceId || 'slot'}-${fromIndex}` }
    : createPlaceholderSlot(fromIndex)
  internalVideoList.value[toIndex] = hasVideoContent(fromSlot)
    ? { ...fromSlot, id: `video-${fromSlot.deviceId || 'slot'}-${toIndex}` }
    : createPlaceholderSlot(toIndex)

  if (activeVideoIndex.value === fromIndex) {
    activeVideoIndex.value = toIndex
  } else if (activeVideoIndex.value === toIndex) {
    activeVideoIndex.value = fromIndex
  }

  persistDashboardVideoState()
  const moved = hasVideoContent(fromSlot) || hasVideoContent(toSlot)
  if (moved) {
    createMessage.success('已调整通道位置')
  }
}

async function handleDrop(targetIndex: number) {
  const sourceIndex = dragSourceIndex.value
  dragSourceIndex.value = null
  dragOverIndex.value = null
  if (sourceIndex === null || sourceIndex === targetIndex) return
  await swapVideoChannels(sourceIndex, targetIndex)
}

// 查找空屏幕
const findEmptyScreen = (): number | null => {
  const maxCount = getMaxVideoCount(currentLayout.value)
  // 确保内部列表已初始化
  if (internalVideoList.value.length === 0) {
    return 0
  }
  
  for (let i = 0; i < maxCount; i++) {
    const video = internalVideoList.value[i]
    // 判断是否为空屏幕：没有视频对象，或者没有URL，或者URL为空字符串
    if (!video || !video.url || video.url.trim() === '') {
      return i
    }
  }
  return null
}

function resolveTargetScreenIndex(): number | null {
  if (currentLayout.value === '1') return 0
  return findEmptyScreen()
}

async function startPlayAtScreen(
  targetIndex: number,
  payload: {
    id: string
    name: string
    url: string
    deviceId?: string
    location?: string
    device?: MonitorTreeDeviceNode
    fallbackUrl?: string | null
    preferAi?: boolean
    aiStatus?: DashboardAiStatus
    playerEngine?: string | null
    videoCodec?: string | null
    playSources?: WvpPlaySourceOption[] | null
    pendingAiUrl?: string | null
  },
) {
  clearAiFallbackTimer(targetIndex)
  aiFallbackPlayingUrls.delete(targetIndex)
  if (videoRefs.value[targetIndex]) {
    const existingInstance = videoRefs.value[targetIndex]
    try {
      if (existingInstance?.destroy && existingInstance.jessibuca) {
        existingInstance.destroy()
      }
    } catch (error) {
      console.warn('销毁现有播放器实例失败:', error)
    }
    videoRefs.value[targetIndex] = null
  }

  const fallbackUrl = payload.fallbackUrl?.trim()
  const hasFallback = !!(payload.preferAi && fallbackUrl && fallbackUrl !== payload.url)
  const pendingAi = payload.pendingAiUrl?.trim()
  const playSources = payload.playSources?.filter((source) => source.url?.trim()) ?? null
  const aiStatus = payload.aiStatus ?? resolveAiStatus(payload.preferAi)

  internalVideoList.value[targetIndex] = {
    id: payload.id,
    url: payload.url,
    name: payload.name,
    deviceId: payload.deviceId,
    location: payload.location || '',
    device: payload.device,
    playerEngine: payload.playerEngine || '',
    videoCodec: payload.videoCodec || '',
    fallbackUrl: hasFallback ? fallbackUrl : null,
    playSources,
    aiStatus,
  }
  persistDashboardVideoState()

  if (pendingAi && pendingAi !== payload.url && payload.deviceId) {
    schedulePendingAiStreamUpgrade(
      pendingAi,
      payload.url,
      () => {
        const slot = internalVideoList.value[targetIndex]
        return !!slot && slot.deviceId === payload.deviceId && slot.url !== pendingAi
      },
      () => {
        const slot = internalVideoList.value[targetIndex]
        if (!slot) return
        internalVideoList.value[targetIndex] = {
          ...slot,
          url: pendingAi,
          fallbackUrl: payload.url,
          aiStatus: 'ai',
        }
      },
    )
  }
  if (!hasFallback) return

  const primaryUrl = payload.url
  const timerId = window.setTimeout(async () => {
    aiFallbackTimers.delete(targetIndex)
    const slot = internalVideoList.value[targetIndex]
    if (!slot || slot.url !== primaryUrl) return
    if (aiFallbackPlayingUrls.get(targetIndex) === primaryUrl) return
    if (videoRefs.value[targetIndex]?.playing) return

    createMessage.warning(
      'AI 流暂不可用（请确认算法任务已启动且媒体服务器已收到 AI 推流），已切换为原始画面（无检测框）',
    )
    internalVideoList.value[targetIndex] = { ...slot, url: fallbackUrl, fallbackUrl: null, aiStatus: 'fallback' }
    persistDashboardVideoState()
  }, AI_PLAY_FALLBACK_MS)
  aiFallbackTimers.set(targetIndex, timerId)
}

function findNextPlaySource(slot: DashboardVideoSlot): WvpPlaySourceOption | null {
  const sources = slot.playSources?.filter((source) => source.url?.trim()) ?? []
  if (!sources.length || !slot.url) return null
  const currentUrl = slot.url.trim()
  const currentIndex = sources.findIndex((source) => source.url.trim() === currentUrl)
  return sources.find((_, index) => index > currentIndex) ?? null
}

function handleVideoStreamError(index: number) {
  const slot = internalVideoList.value[index]
  if (!slot?.url) return
  clearAiFallbackTimer(index)
  aiFallbackPlayingUrls.delete(index)

  const fallbackUrl = slot.fallbackUrl?.trim()
  if (fallbackUrl && fallbackUrl !== slot.url) {
    createMessage.warning('AI 流已中断，已切换为原始画面（无检测框）')
    internalVideoList.value[index] = { ...slot, url: fallbackUrl, fallbackUrl: null, aiStatus: 'fallback' }
    persistDashboardVideoState()
    return
  }

  const nextSource = findNextPlaySource(slot)
  if (!nextSource) return
  createMessage.warning('当前视频流播放失败，正在尝试备用播放源')
  internalVideoList.value[index] = {
    ...slot,
    url: nextSource.url,
    fallbackUrl: null,
    playerEngine: nextSource.playerEngine,
    videoCodec: nextSource.videoCodec,
    aiStatus: slot.aiStatus === 'ai' ? 'fallback' : slot.aiStatus,
  }
  persistDashboardVideoState()
}

async function resolvePlayUrlsForDevice(dev: MonitorTreeDeviceNode) {
  if (isGb28181Device(dev.source, dev.device_kind)) {
    return { url: null as string | null, fallbackUrl: null as string | null | undefined }
  }
  return pickDirectPlayUrls(dev, enableAi.value)
}

async function reloadVideoAtIndex(index: number) {
  const slot = internalVideoList.value[index]
  if (!slot?.url || !slot.deviceId) return

  const playId = slot.deviceId
  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId)
    if (!gb) return
    const deviceNode = (slot as any).device as MonitorTreeDeviceNode | undefined
    let playDevice: MonitorTreeDeviceNode | null | undefined = deviceNode
    if (enableAi.value) {
      const recognition = await ensureGbDashboardAiRecognition(gb, deviceNode)
      playDevice = recognition.device ?? deviceNode
      if (!recognition.ready) {
        enableAi.value = false
      }
    }
    const { url, fallbackUrl, preferAi, playerEngine, videoCodec, playSources, pendingAiUrl } = await resolveGbChannelPlayUrls(
      gb.sipDeviceId,
      gb.channelId,
      { enableAi: enableAi.value, synced: playDevice },
    )
    if (url) {
      await startPlayAtScreen(index, {
        id: slot.id,
        name: slot.name,
        url,
        deviceId: playId,
        location: slot.location,
        fallbackUrl,
        preferAi,
        playerEngine,
        videoCodec,
        playSources,
        device: playDevice ?? undefined,
        pendingAiUrl,
      })
    }
    return
  }

  const dev = (slot as any).device as MonitorTreeDeviceNode | undefined
  if (!dev) return

  const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolvePlayUrlsForDevice(dev)
  if (!url) {
    createMessage.warn(enableAi.value ? '该设备暂无 AI 流或原始流地址' : '该设备暂无播放地址')
    return
  }
  await startPlayAtScreen(index, {
    id: slot.id,
    name: slot.name,
    url,
    deviceId: playId,
    location: slot.location,
    fallbackUrl,
    preferAi,
    pendingAiUrl,
  })
}

async function reloadAllVideosForAiToggle() {
  const tasks: Promise<void>[] = []
  internalVideoList.value.forEach((slot, idx) => {
    if (slot?.url) tasks.push(reloadVideoAtIndex(idx))
  })
  await Promise.all(tasks)
}

let aiToggleRequestSeq = 0

watch(enableAi, async (checked) => {
  const requestSeq = ++aiToggleRequestSeq
  persistEnableAi(checked)
  if (checked) {
    const recognitionReady = await ensureDashboardAiRecognitionForVisibleDevices()
    if (requestSeq !== aiToggleRequestSeq) return
    if (!recognitionReady) {
      enableAi.value = false
      return
    }
  } else {
    await stopDashboardAiRecognitionForVisibleDevices()
    if (requestSeq !== aiToggleRequestSeq) return
  }
  reloadAllVideosForAiToggle()
})

// 播放设备流（与分屏监控一致：默认 /live 原始流，启用 AI 时后台升级）
const playDeviceStream = async (device: any) => {
  const dev: MonitorTreeDeviceNode = (device.device || device) as MonitorTreeDeviceNode
  const playId = String(device.id || dev.id || '')
  const displayName =
    formatCameraShortName(device.name || formatCameraDeviceLabel(dev) || playId) || playId

  const maxCount = getMaxVideoCount(currentLayout.value)
  if (internalVideoList.value.length === 0) {
    ensureVideoSlots(maxCount)
  }

  const targetIndex = resolveTargetScreenIndex()
  if (targetIndex === null) {
    createMessage.warning('当前没有空屏幕，请先移除占用通道后再试')
    return
  }

  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId)
    if (!gb) {
      createMessage.warning('无效国标通道')
      return
    }
    let playDevice: MonitorTreeDeviceNode | null | undefined = dev
    if (enableAi.value) {
      const recognition = await ensureGbDashboardAiRecognition(gb, dev)
      playDevice = recognition.device ?? dev
      if (!recognition.ready) {
        enableAi.value = false
      }
    }
    const { url, fallbackUrl, preferAi, playerEngine, videoCodec, playSources, pendingAiUrl } = await resolveGbChannelPlayUrls(
      gb.sipDeviceId,
      gb.channelId,
      { enableAi: enableAi.value, synced: playDevice },
    )
    if (!url) {
      createMessage.warn(
        enableAi.value
          ? '国标通道 AI 流不可用，请确认算法任务已启动；WVP 点播也失败，请检查通道状态'
          : '国标通道拉流失败，请检查 WVP 服务与通道状态',
      )
      return
    }
    await startPlayAtScreen(targetIndex, {
      id: `video-${playId}-${targetIndex}`,
      name: displayName,
      url,
      deviceId: playId,
      location: device.location || '',
      device: playDevice ?? dev,
      fallbackUrl,
      preferAi,
      playerEngine,
      videoCodec,
      playSources,
      pendingAiUrl,
    })
    return
  }

  if (isGb28181Device(dev.source, dev.device_kind)) {
    createMessage.info('请展开上级国标设备并选择通道')
    return
  }

  if (enableAi.value) {
    const recognitionReady = await ensureDashboardAiRecognitionForDevices(collectDashboardAiDevices(dev))
    if (!recognitionReady) {
      enableAi.value = false
    }
  }

  const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolvePlayUrlsForDevice(dev)
  if (!url) {
    createMessage.warning(
      enableAi.value
        ? '该设备暂无 AI 流或原始流播放地址，请先在设备列表中配置'
        : '该设备暂无可用播放地址，请先在设备列表中配置流地址',
    )
    return
  }

  await startPlayAtScreen(targetIndex, {
    id: `video-${playId}-${targetIndex}`,
    name: displayName,
    url,
    deviceId: playId,
    location: device.location || '',
    device: dev,
    fallbackUrl,
    preferAi,
    pendingAiUrl,
  })
}

// 更新时间
const updateTime = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 监听设备变化
watch(() => props.device, (newDevice) => {
  if (newDevice) {
    // 这里可以加载新设备的视频流
  }
}, { immediate: true })

// 监听视频列表变化
watch(() => props.videoList, (newList) => {
  if (skipDefaultVideoInit.value) return
  if (newList && newList.length > 0) {
    // 如果内部列表为空，则初始化内部列表
    if (internalVideoList.value.length === 0) {
      internalVideoList.value = newList.map((v, i) => ({
        ...v,
        id: v.id || `video-${i}`,
        url: v.url || '',
        name: v.name || `视频${i + 1}`
      }))
    }
  }
}, { immediate: true })

// 监听布局变化，调整内部视频列表
watch(() => currentLayout.value, (newLayout) => {
  const maxCount = getMaxVideoCount(newLayout)
  // 如果当前列表长度超过新布局的最大数量，截断
  if (internalVideoList.value.length > maxCount) {
    internalVideoList.value = internalVideoList.value.slice(0, maxCount)
  }
  persistDashboardVideoState()
})

// 监听正在播放的视频列表变化，通知父组件
watch(activeVideos, (newVideos) => {
  emit('video-list-change', newVideos.map(v => ({ name: v.name, id: v.id })))
}, { deep: true, immediate: true })

// 检查滚动状态
const checkScrollStatus = () => {
  if (!scrollContainerRef.value) return
  const container = scrollContainerRef.value
  canScrollLeft.value = container.scrollLeft > 0
  canScrollRight.value = container.scrollLeft < container.scrollWidth - container.clientWidth - 1
}

// 向左滑动
const scrollLeft = () => {
  if (!scrollContainerRef.value) return
  const container = scrollContainerRef.value
  const scrollAmount = 220 // 每次滑动一个卡片宽度 + gap
  container.scrollBy({
    left: -scrollAmount,
    behavior: 'smooth'
  })
}

// 向右滑动
const scrollRight = () => {
  if (!scrollContainerRef.value) return
  const container = scrollContainerRef.value
  const scrollAmount = 220 // 每次滑动一个卡片宽度 + gap
  container.scrollBy({
    left: scrollAmount,
    behavior: 'smooth'
  })
}

// 处理滚动事件
const handleScroll = () => {
  checkScrollStatus()
}

// 格式化时间
const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}

// 处理录像点击
const playAlertRecord = async (record: any) => {
  if (!openPlayerModal || typeof openPlayerModal !== 'function') {
    createMessage.error('播放器未初始化，请刷新页面重试')
    return
  }

  if (isSnapAlertTask(record)) {
    createMessage.warn('抓拍任务无告警录像')
    return
  }

  if (!record.device_id || !record.time) {
    createMessage.warn('缺少必要信息：设备ID或告警时间')
    return
  }

  try {
    const ok = await playAlertRecordInModal(
      { openModal: openPlayerModal, closeModal: closePlayerModal, setModalProps: setPlayerModalProps },
      record,
    )
    if (ok) {
      lastVideoErrorTime = 0
      lastVideoErrorMsg = ''
    } else {
      showVideoErrorOnce('暂未找到该时间段的录像文件，请稍后再试')
    }
  } catch (error: any) {
    console.error('查询录像失败:', error)
    const errorData = error?.response?.data || error?.data
    showVideoErrorOnce(errorData?.message || error?.message || '查询录像失败，请稍后重试')
  }
}

const handleRecordClick = playAlertRecord

// 暴露方法给父组件
defineExpose({
  playDeviceStream,
  playAlertRecord,
})

let timeTimer: any = null
let scrollCheckTimer: any = null
let isMounted = false

onMounted(() => {
  isMounted = true
  
  updateTime()
  timeTimer = setInterval(updateTime, 1000)

  if (internalVideoList.value.some((slot) => slot?.pendingRestore)) {
    restorePendingVideos()
  }
  
  // 等待DOM渲染后检查滚动状态
  scrollCheckTimer = setTimeout(() => {
    if (isMounted) {
      checkScrollStatus()
    }
  }, 100)
  
  // 监听窗口大小变化
  window.addEventListener('resize', checkScrollStatus)

  resumeDashboardVideosAfterRouteReturn()
})

onActivated(() => {
  resumeDashboardVideosAfterRouteReturn()
})

onUnmounted(() => {
  isMounted = false
  
  if (scrollCheckTimer) {
    clearTimeout(scrollCheckTimer)
    scrollCheckTimer = null
  }
  
  if (timeTimer) {
    clearInterval(timeTimer)
    timeTimer = null
  }
  
  window.removeEventListener('resize', checkScrollStatus)
  
  // 清理所有视频播放器实例
  videoRefs.value.forEach((ref) => {
    if (ref?.jessibuca) {
      ref._unmounting = true
    }
  })
  videoRefs.value = []

  aiFallbackTimers.forEach((id) => window.clearTimeout(id))
  aiFallbackTimers.clear()
  aiFallbackPlayingUrls.clear()
})

// 监听告警列表变化，更新滚动状态
watch(() => alertRecordList.value, () => {
  setTimeout(() => {
    checkScrollStatus()
  }, 100)
}, { deep: true })
</script>

<style lang="less" scoped>
.video-monitor {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--dashboard-panel);
  border: 1px solid var(--dashboard-border);
  box-shadow: 0 16px 42px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  border-radius: var(--dashboard-radius);
  padding: 3px;
  position: relative;
  z-index: 10;
  min-height: 0;
  overflow: hidden;

  &.preset-panel-open {
    overflow: visible;
  }

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      linear-gradient(90deg, rgba(56, 189, 248, 0.08), transparent 34%, transparent 70%, rgba(245, 158, 11, 0.05)),
      radial-gradient(circle at top left, rgba(56, 189, 248, 0.12), transparent 46%);
    pointer-events: none;
    border-radius: var(--dashboard-radius);
  }
}

.monitor-header {
  flex-shrink: 0;
  height: auto;
  min-height: 52px;
  background: rgba(8, 22, 39, 0.72);
  border-bottom: 1px solid var(--dashboard-border);
  color: var(--dashboard-text);
  font-size: 14px;
  padding: 8px clamp(12px, 1.2vw, 20px);
  display: flex;
  align-items: center;
  align-content: center;
  gap: 8px 12px;
  flex-wrap: wrap;
  position: relative;
  z-index: 1;
  overflow: visible;

  &.panel-open {
    z-index: 210;
  }

  .header-title {
    flex: 0 0 auto;
    min-width: 72px;
    font-size: 14px;
    line-height: 32px;
    font-weight: 600;
    color: var(--dashboard-text);
    white-space: nowrap;
  }

  .header-time {
    flex: 0 0 auto;
    min-width: 152px;
    font-size: 14px;
    line-height: 32px;
    color: var(--dashboard-muted);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .header-location {
    flex: 1 1 140px;
    min-width: 0;
    max-width: 180px;
    font-size: 14px;
    line-height: 32px;
    color: var(--dashboard-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .enable-ai-wrap {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    height: 32px;
    padding: 0 12px;
    background: rgba(56, 189, 248, 0.1);
    border: 1px solid var(--dashboard-border);
    border-radius: var(--dashboard-radius);

    :deep(.ant-checkbox-wrapper) {
      color: var(--dashboard-text) !important;
      font-size: 14px;
      line-height: 1;
      white-space: nowrap;
    }

    :deep(.ant-checkbox-wrapper:hover .ant-checkbox-inner) {
      border-color: var(--dashboard-blue) !important;
    }

    :deep(.ant-checkbox .ant-checkbox-inner) {
      width: 16px;
      height: 16px;
      background-color: rgba(7, 19, 34, 0.72) !important;
      border-color: rgba(56, 189, 248, 0.48) !important;
    }

    :deep(.ant-checkbox-checked .ant-checkbox-inner) {
      background-color: var(--dashboard-blue) !important;
      border-color: var(--dashboard-blue) !important;
    }

    :deep(.ant-checkbox-checked .ant-checkbox-inner::after) {
      border-color: #fff !important;
    }

    :deep(.ant-checkbox + span) {
      color: var(--dashboard-text) !important;
      padding-inline-start: 8px;
    }
  }

  .header-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-left: auto;
    flex-shrink: 0;
  }

  .split-toolbar {
    flex: 1 1 340px;
    min-width: 300px;
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: flex-end;
    flex-wrap: wrap;
    margin-left: auto;

    .split-btn {
      min-width: 60px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid var(--dashboard-border);
      border-radius: var(--dashboard-radius);
      cursor: pointer;
      transition: background 0.2s, border-color 0.2s, transform 0.2s;
      color: var(--dashboard-muted);
      font-size: 12px;
      padding: 0 8px;
      white-space: nowrap;

      &:hover {
        background: rgba(56, 189, 248, 0.18);
        border-color: var(--dashboard-border-strong);
        color: var(--dashboard-text);
        transform: translateY(-1px);
      }

      &.active {
        background: rgba(56, 189, 248, 0.2);
        border-color: var(--dashboard-blue);
        color: var(--dashboard-text);
        box-shadow: inset 0 -2px 0 var(--dashboard-blue);
      }
    }
  }

  .toolbar-trigger {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--dashboard-radius);
    border: 1px solid var(--dashboard-border);
    background: rgba(56, 189, 248, 0.1);
    color: var(--dashboard-text);
    font-size: 12px;
    cursor: pointer;
    transition: background 0.2s, border-color 0.2s, transform 0.2s;
    white-space: nowrap;
    user-select: none;
    flex-shrink: 0;

    &:hover,
    &.open {
      background: rgba(56, 189, 248, 0.18);
      border-color: var(--dashboard-border-strong);
      color: var(--dashboard-text);
      transform: translateY(-1px);
    }

    .trigger-label {
      font-weight: 500;
    }
  }

  .layout-preset-trigger {
    &.has-active {
      border-color: rgba(34, 197, 94, 0.42);
    }

    .trigger-badge {
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
      padding: 0 6px;
      height: 20px;
      line-height: 20px;
      border-radius: var(--dashboard-radius);
      font-size: 11px;
      color: #bbf7d0;
      background: rgba(34, 197, 94, 0.14);
      border: 1px solid rgba(34, 197, 94, 0.28);
    }
  }
}

.preset-panel-backdrop {
  position: absolute;
  inset: 0;
  top: 52px;
  z-index: 150;
  background: rgba(0, 0, 0, 0.42);
  pointer-events: auto;
}

@media (max-width: 1440px) {
  .monitor-header {
    padding: 8px 12px;
    gap: 8px;

    .split-toolbar {
      flex-basis: 100%;
      min-width: 0;
      justify-content: flex-start;
      margin-left: 0;
    }
  }
}

@media (max-width: 960px) {
  .monitor-header {
    align-items: flex-start;

    .header-time {
      min-width: 132px;
      font-size: 13px;
    }

    .header-location {
      max-width: 140px;
      font-size: 13px;
    }

    .split-toolbar {
      gap: 6px;

      .split-btn {
        min-width: 52px;
        padding: 0 6px;
      }
    }
  }
}

.monitor-content {
  flex: 1;
  min-height: 0;
  display: grid;
  gap: 4px;
  padding: 4px;
  overflow: hidden;
  background:
    linear-gradient(rgba(56, 189, 248, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(56, 189, 248, 0.08) 1px, transparent 1px),
    radial-gradient(circle at 50% 0%, rgba(56, 189, 248, 0.08), transparent 42%);
  background-size: 24px 24px, 24px 24px, auto;
  background-color: #02070f;

  // 1分屏 - 全屏单画面
  &.layout-1 {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr;
  }

  // 4分屏 - 2行2列
  &.layout-4 {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(2, 1fr);
  }

  // 9分屏 - 3行3列
  &.layout-9 {
    grid-template-columns: repeat(3, 1fr);
    grid-template-rows: repeat(3, 1fr);
  }

  // 16分屏 - 4行4列
  &.layout-16 {
    grid-template-columns: repeat(4, 1fr);
    grid-template-rows: repeat(4, 1fr);
  }
}

.video-window {
  position: relative;
  background: #000;
  border: 1px solid rgba(56, 189, 248, 0.22);
  border-radius: 3px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: var(--dashboard-border-strong);
    transform: scale(1.003);
    z-index: 10;
  }

  &.active {
    border-color: var(--dashboard-blue);
    box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.24), 0 0 18px rgba(56, 189, 248, 0.18);
    z-index: 5;
  }

  &.drag-over {
    border-color: var(--dashboard-green);
    box-shadow: 0 0 12px rgba(34, 197, 94, 0.42);
    z-index: 12;
  }

  &.is-dragging {
    opacity: 0.55;
    border-style: dashed;
  }

  .video-container {
    width: 100%;
    height: 100%;
    position: relative;
    background: #000;

    .video-placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: rgba(184, 203, 224, 0.48);

      .camera-icon {
        width: 64px;
        height: 64px;
        opacity: 0.58;
        filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.46));
        transition: opacity 0.2s, transform 0.2s;
      }

      &:hover .camera-icon {
        opacity: 0.78;
        transform: scale(1.04);
      }

      .placeholder-text {
        margin-top: 8px;
        font-size: 12px;
        color: var(--dashboard-muted);
      }
    }

    .video-player {
      width: 100%;
      height: 100%;
    }

    .drag-zone {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 72px;
      height: 56px;
      max-width: 32%;
      max-height: 28%;
      z-index: 1;
      cursor: grab;
      border-radius: var(--dashboard-radius);

      &:active {
        cursor: grabbing;
      }
    }

    .video-label {
      position: absolute;
      top: 0;
      left: 0;
      right: 112px;
      color: var(--dashboard-text);
      font-size: 12px;
      padding: 4px 8px;
      text-align: left;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      background: linear-gradient(to bottom, rgba(0, 0, 0, 0.78), transparent);
      z-index: 2;
      pointer-events: none;
    }

    .ai-status-tag {
      position: absolute;
      top: 4px;
      right: 32px;
      z-index: 3;
      min-width: 42px;
      max-width: 74px;
      height: 22px;
      padding: 0 7px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--dashboard-radius);
      border: 1px solid rgba(56, 189, 248, 0.34);
      background: rgba(5, 14, 26, 0.74);
      color: var(--dashboard-text);
      font-size: 11px;
      line-height: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      pointer-events: none;
      box-shadow: 0 6px 14px rgba(0, 0, 0, 0.24);
    }

    .ai-status-tag--original {
      color: #bdefff;
      border-color: rgba(56, 189, 248, 0.36);
      background: rgba(56, 189, 248, 0.14);
    }

    .ai-status-tag--ai {
      color: #bbf7d0;
      border-color: rgba(34, 197, 94, 0.38);
      background: rgba(34, 197, 94, 0.16);
    }

    .ai-status-tag--fallback {
      color: #fed7aa;
      border-color: rgba(245, 158, 11, 0.42);
      background: rgba(245, 158, 11, 0.16);
    }

    .ai-status-tag--unavailable {
      color: rgba(230, 241, 255, 0.72);
      border-color: rgba(184, 203, 224, 0.28);
      background: rgba(184, 203, 224, 0.12);
    }

    .video-close-btn {
      position: absolute;
      top: 4px;
      right: 4px;
      z-index: 3;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      padding: 0;
      border: none;
      border-radius: var(--dashboard-radius);
      color: #fff;
      background: rgba(249, 115, 115, 0.58);
      cursor: pointer;
      opacity: 0.75;
      transition: opacity 0.2s, background 0.2s;

      &:hover {
        opacity: 1;
        background: rgba(249, 115, 115, 0.85);
      }
    }

    .video-active-indicator {
      position: absolute;
      top: 4px;
      left: 4px;
      width: 8px;
      height: 8px;
      background: var(--dashboard-blue);
      border-radius: 50%;
      box-shadow: 0 0 8px rgba(56, 189, 248, 0.78);
    }
  }
}

.alert-record-list {
  flex-shrink: 0;
  height: 140px;
  min-height: 140px;
  background: rgba(5, 14, 26, 0.52);
  border-top: 1px solid var(--dashboard-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  z-index: 2;
}

.alert-record-header {
  height: 36px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(8, 22, 39, 0.78);
  border-bottom: 1px solid var(--dashboard-border);

  .header-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--dashboard-text);
  }

  .header-count {
    font-size: 12px;
    color: var(--dashboard-muted);
  }
}

.alert-record-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.scroll-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(56, 189, 248, 0.2);
  border: 1px solid var(--dashboard-border-strong);
  border-radius: 50%;
  cursor: pointer;
  z-index: 10;
  transition: background 0.2s, border-color 0.2s, transform 0.2s;
  color: var(--dashboard-text);
  backdrop-filter: blur(4px);

  &:hover {
    background: rgba(56, 189, 248, 0.3);
    border-color: var(--dashboard-blue);
    transform: translateY(-50%) scale(1.1);
  }

  &:active {
    transform: translateY(-50%) scale(0.95);
  }
}

.scroll-btn-left {
  left: 8px;
}

.scroll-btn-right {
  right: 8px;
}

.alert-record-scroll {
  width: 100%;
  height: 100%;
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  overflow-x: auto;
  overflow-y: hidden;
  align-items: center;
  scroll-behavior: smooth;

  &::-webkit-scrollbar {
    height: 6px;
  }

  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.04);
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(56, 189, 248, 0.48);
    border-radius: 3px;

    &:hover {
      background: rgba(56, 189, 248, 0.66);
    }
  }
}

.alert-record-item {
  flex-shrink: 0;
  width: 200px;
  height: 100%;
  background: rgba(7, 19, 34, 0.76);
  border: 1px solid var(--dashboard-border);
  border-radius: var(--dashboard-radius);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.2s;

  &:hover {
    background: rgba(12, 31, 55, 0.92);
    border-color: var(--dashboard-border-strong);
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
  }
}

.record-thumbnail {
  position: relative;
  width: 100%;
  height: 80px;
  border-radius: var(--dashboard-radius);
  overflow: hidden;
  background: rgba(0, 0, 0, 0.3);

  .thumbnail-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .thumbnail-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: rgba(184, 203, 224, 0.5);
  }

  .record-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    padding: 2px 6px;
    border-radius: var(--dashboard-radius);
    font-size: 10px;
    font-weight: 500;
    background: rgba(56, 189, 248, 0.82);
    color: #ffffff;
    border: 1px solid var(--dashboard-blue);
  }
}

.record-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.record-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--dashboard-text);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-meta {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 0;
  font-size: 11px;
  color: var(--dashboard-muted);

  .record-device {
    flex: 0 1 auto;
    max-width: 130px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
  }

  .record-time {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--dashboard-text);
    margin-left: 8px;
    white-space: nowrap;
  }

  .play-icon {
    flex-shrink: 0;
    color: var(--dashboard-blue);
    margin-left: 8px;
    cursor: pointer;
    transition: all 0.3s;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      color: #7dd3fc;
      transform: scale(1.1);
    }
  }
}

.empty-records {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(184, 203, 224, 0.52);
  font-size: 12px;
}

.boxfoot {
  position: absolute;
  bottom: 0;
  width: 100%;
  left: 0;
  pointer-events: none;

  &:before, &:after {
    position: absolute;
    width: 17px;
    height: 17px;
    content: "";
    border-bottom: 2px solid var(--dashboard-border-strong);
    bottom: -2px;
  }

  &:before {
    border-left: 2px solid var(--dashboard-border-strong);
    left: -2px;
  }

  &:after {
    border-right: 2px solid var(--dashboard-border-strong);
    right: -2px;
  }
}

/* Command-center visual layer: preserve playback behavior, clarify hierarchy. */
.video-monitor {
  padding: 0;
  background: var(--dashboard-panel);
  border-color: var(--dashboard-border);
  border-radius: 2px;
  box-shadow: none;

  &::before {
    display: none;
  }
}

.monitor-header {
  min-height: 54px;
  padding: 8px 12px;
  gap: 8px 10px;
  background: var(--dashboard-panel-strong);
  border-color: var(--dashboard-border);

  .header-title {
    position: relative;
    padding-left: 11px;
    color: var(--dashboard-text);
    font-size: 14px;

    &::before {
      content: '';
      position: absolute;
      top: 9px;
      bottom: 9px;
      left: 0;
      width: 2px;
      background: var(--dashboard-cyan);
    }
  }

  .header-time {
    color: var(--dashboard-weak);
    font-family: 'IBM Plex Mono', Consolas, monospace;
    font-size: 12px;
  }

  .header-location {
    color: var(--dashboard-muted);
    font-size: 12px;
  }

  .enable-ai-wrap,
  .toolbar-trigger,
  .split-toolbar .split-btn {
    background: #0a1921;
    border-color: var(--dashboard-border);
    border-radius: 2px;
    box-shadow: none;
  }

  .enable-ai-wrap {
    padding: 0 10px;
  }

  .split-toolbar {
    gap: 4px;

    .split-btn {
      min-width: 52px;
      transition: color 0.18s, border-color 0.18s, background 0.18s;

      &:hover {
        color: var(--dashboard-text);
        background: rgba(38, 213, 228, 0.07);
        border-color: var(--dashboard-border-strong);
        transform: none;
      }

      &.active {
        color: var(--dashboard-cyan);
        background: rgba(38, 213, 228, 0.09);
        border-color: var(--dashboard-cyan);
        box-shadow: inset 0 -2px 0 var(--dashboard-cyan);
      }
    }
  }

  .toolbar-trigger:hover,
  .toolbar-trigger.open {
    color: var(--dashboard-cyan);
    background: rgba(38, 213, 228, 0.08);
    border-color: var(--dashboard-cyan);
    transform: none;
  }
}

.monitor-content {
  gap: 6px;
  padding: 6px;
  background: #03090d;
}

.video-window {
  background: #010506;
  border-color: #17333e;
  border-radius: 0;
  transition: border-color 0.18s, box-shadow 0.18s;

  &:hover {
    border-color: var(--dashboard-border-strong);
    transform: none;
  }

  &.active {
    border-color: var(--dashboard-cyan);
    box-shadow: inset 0 0 0 1px var(--dashboard-cyan);
  }

  &.drag-over {
    border-color: var(--dashboard-green);
    box-shadow: inset 0 0 0 1px var(--dashboard-green);
  }

  .video-container {
    .video-label {
      top: auto;
      right: 0;
      bottom: 0;
      padding: 18px 10px 7px;
      color: var(--dashboard-text);
      background: linear-gradient(to top, rgba(0, 0, 0, 0.82), transparent);
      font-family: 'IBM Plex Mono', Consolas, monospace;
      font-size: 11px;
    }

    .video-active-indicator {
      top: 8px;
      left: 8px;
      width: 7px;
      height: 7px;
      background: var(--dashboard-cyan);
      box-shadow: 0 0 0 3px rgba(38, 213, 228, 0.12);
    }

    .ai-status-tag {
      border-radius: 999px;
      box-shadow: none;
    }
  }
}

.alert-record-list {
  height: 126px;
  min-height: 126px;
  background: #071219;
  border-color: var(--dashboard-border);
}

.alert-record-header {
  height: 34px;
  padding: 0 12px;
  background: var(--dashboard-panel-strong);
  border-color: var(--dashboard-border);

  .header-title {
    position: relative;
    padding-left: 10px;
    font-size: 12px;

    &::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 0;
      width: 5px;
      height: 5px;
      background: var(--dashboard-amber);
      border-radius: 50%;
      transform: translateY(-50%);
    }
  }
}

.alert-record-scroll {
  gap: 0;
  padding: 0;
  align-items: stretch;
}

.alert-record-item {
  width: 224px;
  height: auto;
  padding: 10px 12px;
  gap: 4px;
  justify-content: center;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--dashboard-border);
  border-radius: 0;
  transition: color 0.18s, background 0.18s;

  &:hover {
    background: rgba(245, 185, 66, 0.06);
    border-color: var(--dashboard-border);
    box-shadow: inset 0 -2px 0 var(--dashboard-amber);
    transform: none;
  }
}

.record-title {
  font-size: 12px;
}

.record-meta {
  .record-time {
    color: var(--dashboard-muted);
    font-family: 'IBM Plex Mono', Consolas, monospace;
    font-size: 11px;
  }

  .play-icon {
    color: var(--dashboard-cyan);
  }
}

.scroll-btn {
  width: 30px;
  height: 44px;
  color: var(--dashboard-cyan);
  background: rgba(6, 16, 23, 0.9);
  border-color: var(--dashboard-border-strong);
  border-radius: 1px;
  backdrop-filter: none;

  &:hover {
    background: #0d222c;
    border-color: var(--dashboard-cyan);
    transform: translateY(-50%);
  }
}

.boxfoot {
  display: none;
}

@media (max-width: 1180px) {
  .monitor-header {
    .header-time {
      min-width: 126px;
    }

    .header-location {
      max-width: 126px;
    }

    .header-toolbar {
      width: 100%;
    }

    .split-toolbar {
      flex-basis: auto;
      min-width: 0;
      justify-content: flex-start;
      margin-left: 0;
    }
  }
}

@media (max-width: 767px) {
  .video-monitor {
    height: 100%;
    min-height: 480px;
  }

  .monitor-header {
    min-height: 98px;
    align-items: center;
    padding: 7px 8px;

    .header-title {
      line-height: 30px;
    }

    .header-time,
    .header-location {
      display: none;
    }

    .enable-ai-wrap {
      margin-left: auto;
    }

    .header-toolbar {
      width: 100%;
      min-width: 0;
      gap: 6px;
      overflow: hidden;
    }

    .split-toolbar {
      flex: 1 1 auto;
      min-width: 0;
      flex-wrap: nowrap;
      justify-content: flex-start;
      margin-left: 0;
      overflow-x: auto;
      overflow-y: hidden;
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }

      .split-btn {
        flex: 0 0 auto;
        min-width: 54px;
      }
    }

    .layout-preset-trigger {
      flex: 0 0 auto;

      .trigger-badge {
        display: none;
      }
    }
  }

  .monitor-content {
    flex: 1 1 auto;
    min-height: 238px;
    display: block;
  }

  .monitor-content .video-window {
    display: none !important;
    width: 100% !important;
    height: 100% !important;
    inset: auto !important;
    position: relative !important;
  }

  .monitor-content .video-window.active {
    display: block !important;
  }

  .alert-record-list {
    height: 142px;
    min-height: 142px;
  }

  .alert-record-item {
    width: 210px;
  }
}
</style>
