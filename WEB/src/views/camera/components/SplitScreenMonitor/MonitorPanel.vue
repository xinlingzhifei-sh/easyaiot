<template>
  <div :class="['split-screen-container', { 'fullscreen-mode': state.isFull, 'preset-panel-open': presetPanelOpen }]">
    <a-layout class="monitor-layout">
      <a-layout-sider :width="350" class="device-tree-sider" theme="light">
        <CollapseContainer :can-expan="true" class="tree-container">
          <template #title>
            <div class="tree-header-title">
              <span class="tree-header-title__icon">
                <Icon icon="ant-design:folder-outlined" :size="16" />
              </span>
              <span class="tree-header-title__text">设备目录</span>
            </div>
          </template>
          <template #action="{ expand, onClick }">
            <div class="tree-header-actions">
              <AButton :loading="treeLoading || treeRefreshing" @click.stop="handleRefresh">
                <template #icon>
                  <Icon icon="ant-design:reload-outlined" />
                </template>
                刷新
              </AButton>
              <BasicArrow up :expand="expand" @click="onClick" />
            </div>
          </template>
          <div class="tree-scroll">
            <BasicTree
              search
              :showIcon="true"
              :indent="12"
              v-model:selectedKeys="selectedKeys"
              :expanded-keys="expandedKeys"
              :tree-data="treeData"
              :load-data="onLoadGbDeviceChannels"
              :field-names="{ key: 'key', title: 'title' }"
              class="device-tree"
              @select="handleTreeSelect"
              @update:expanded-keys="expandedKeys = $event"
            />
          </div>
        </CollapseContainer>
      </a-layout-sider>

      <a-layout class="monitor-content-layout">
        <a-layout-header :class="['toolbar-header', { 'panel-open': presetPanelOpen }]">
          <div class="toolbar-content">
            <div class="header-toolbar">
              <a-radio-group
                v-model:value="state.splitMode"
                size="default"
                button-style="solid"
                class="split-mode-group"
                @change="handleSplitModeChange"
              >
                <a-radio-button :value="1">1分屏</a-radio-button>
                <a-radio-button :value="4">4分屏</a-radio-button>
                <a-radio-button :value="9">9分屏</a-radio-button>
                <a-radio-button :value="16">16分屏</a-radio-button>
              </a-radio-group>
              <div
                :class="['layout-preset-trigger', { open: presetPanelOpen, 'has-active': !!activePresetId }]"
                @click="presetPanelOpen = !presetPanelOpen"
              >
                <Icon icon="ant-design:layout-outlined" :size="15" />
                <span class="trigger-label">布局方案</span>
                <span v-if="activePresetSummary" class="trigger-badge">{{ activePresetSummary }}</span>
                <Icon :icon="presetPanelOpen ? 'ant-design:up-outlined' : 'ant-design:down-outlined'" :size="12" />
              </div>
            </div>

            <a-divider type="vertical" class="toolbar-divider" />

            <div class="toolbar-section">
              <a-checkbox v-model:checked="enableAi">启用 AI</a-checkbox>
            </div>

            <a-divider type="vertical" class="toolbar-divider" />

            <div class="toolbar-section">
              <Space size="small">
                <Button
                  type="default"
                  danger
                  size="middle"
                  :disabled="!state.playCells[state.playerIdx]"
                  @click="handleGridDelete"
                >
                  删除选中
                </Button>
                <Button :type="state.isFull ? 'default' : 'primary'" size="middle" @click="handleGridFull">
                  {{ state.isFull ? '退出全屏' : '全屏展示' }}
                </Button>
                <Button type="default" size="middle" @click="handleClearAll">清空全部</Button>
              </Space>
            </div>

            <div class="toolbar-section toolbar-status">
              <span class="status-text">已加载: {{ loadedCount }}/{{ state.splitMode }}</span>
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
        </a-layout-header>

        <div v-if="presetPanelOpen" class="preset-panel-backdrop" @click="presetPanelOpen = false" />

        <a-layout-content class="video-content">
          <div :class="['video-grid', `grid-${state.splitMode}`]" :style="gridStyle">
            <div
              v-for="i in state.splitMode"
              :key="i"
              :class="['video-cell', {
                'cell-selected': state.playerIdx === i - 1,
                'cell-empty': !state.playCells[i - 1],
                'cell-loading': state.loadingCells.includes(i - 1)}]"
              @click="state.playerIdx = i - 1"
            >
              <div v-if="!state.playCells[i - 1]" class="empty-cell">
                <Icon icon="ant-design:video-camera-add-outlined" class="empty-icon" />
                <span class="empty-text">通道 {{ i }}</span>
                <span class="empty-hint">点击左侧摄像头即可播放</span>
              </div>
              <div v-else class="player-wrapper">
                <div v-if="!state.playCells[i - 1]!.url" class="empty-cell restoring-cell">
                  <Icon icon="ant-design:loading-outlined" class="empty-icon spinning" />
                  <span class="empty-text">{{ state.playCells[i - 1]!.name }}</span>
                  <span class="empty-hint">正在恢复播放...</span>
                </div>
                <Jessibuca
                  v-else
                  :key="`player-${i - 1}-${state.playCells[i - 1]!.deviceId}`"
                  :ref="(el) => setPlayerRef(el, i - 1)"
                  :playUrl="state.playCells[i - 1]!.url"
                  :hasAudio="false"
                  :playerEngine="state.playCells[i - 1]!.playerEngine || ''"
                  :videoCodec="state.playCells[i - 1]!.videoCodec || ''"
                  @playing="handleCellPlaying(i - 1, $event)"
                  :fill-video="true"
                  :multi-view="state.splitMode > 1"
                  :ai-with-fallback="!!state.playCells[i - 1]!.fallbackUrl"
                  @stream-error="handleCellStreamError(i - 1)"
                />
                <span v-if="state.playCells[i - 1]!.url" class="cell-name" :title="state.playCells[i - 1]!.name">
                  {{ state.playCells[i - 1]!.name }}
                </span>
                <Button
                  type="text"
                  size="small"
                  danger
                  class="cell-close-btn"
                  @click.stop="handleCellDelete(i - 1)"
                >
                  <Icon icon="ant-design:close-outlined" />
                </Button>
              </div>
            </div>
          </div>
        </a-layout-content>
      </a-layout>
    </a-layout>

  </div>
</template>

<script lang="ts" setup>
import { computed, type CSSProperties, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import {
  Layout as ALayout,
  LayoutSider as ALayoutSider,
  LayoutHeader as ALayoutHeader,
  LayoutContent as ALayoutContent,
  Button as AButton,
  Space,
  Divider as ADivider,
  RadioGroup as ARadioGroup,
  RadioButton as ARadioButton,
  Checkbox as ACheckbox,
} from 'ant-design-vue';
import { BasicTree, type TreeItem } from '@/components/Tree';
import { BasicArrow } from '@/components/Basic';
import { Icon } from '@/components/Icon';
import { CollapseContainer } from '@/components/Container';
import {
  syncGb28181Devices,
  type MonitorTreeDeviceNode} from '@/api/device/camera';
import { formatCameraDeviceLabel, isGb28181Device } from '@/views/camera/utils/deviceLabel';
import {
  AI_PLAY_FALLBACK_MS,
  loadGbChannelSyncedDevice,
  pickDirectPlayUrls,
  schedulePendingAiStreamUpgrade,
  resolveGbChannelPlayUrls} from '@/views/camera/utils/devicePlay';
import {
  collectMonitorTreeExpandedKeys,
  findMonitorDeviceById,
  findMonitorGbDeviceByChannel,
  findMonitorTreeNodeByKey,
} from '@/views/camera/utils/monitorDeviceTree';
import {
  buildWvpChannelTreeNodes,
  parseGbChannelKey,
  type GbChannelRef} from '@/views/camera/utils/gb28181Tree';
import { getDeviceChannels } from '@/api/device/gb28181';
import type { WvpPlaySourceOption } from '@/views/camera/utils/livePlayer';
import { collectWvpGbChannelsForSync } from '@/views/camera/utils/wvpGbSync';
import {
  enrichWvpChannelTreeNodes,
  resolveMonitorGbChannelDisplayName} from '@/views/camera/utils/monitorGbDisplay';
import { getCachedMonitorDirectoryTreeBundle } from '@/views/camera/utils/monitorDirectoryTreeCache';
import {
  invalidateMonitorDirectoryTreeCache,
  loadMonitorDirectoryTreeWithCache,
  type MonitorDirectoryTreeBundle} from '@/views/camera/utils/monitorDirectoryTreeLoad';
import type { TreeProps } from 'ant-design-vue';
import { useMessage } from '@/hooks/web/useMessage';
import Jessibuca from '@/components/Player/module/jessibuca.vue';
import { Button } from '@/components/Button';
import LayoutPresetPanel from './LayoutPresetPanel.vue';
import {
  createAlgorithmTask,
  listAlgorithmTasks,
  startAlgorithmTask,
  stopAlgorithmTask,
} from '@/api/device/algorithm_task';
import { getModelPage } from '@/api/device/model';
import {
  extractDashboardGuardErrorMessage,
  startDashboardGuardTask,
  stopDashboardGuardTask,
  type DashboardGuardScope,
  type DashboardGuardTaskApi,
} from '@/views/dashboard/monitor/dashboardGuardTask';
import {
  loadCameraMonitorLayoutStorage,
  saveCameraMonitorLayoutStorage,
  serializeDeviceSnapshot,
  MAX_CAMERA_MONITOR_LAYOUT_PRESETS,
  type CameraMonitorLayoutPreset,
  type CameraMonitorLayoutSlot,
} from '@/views/camera/utils/monitorLayoutStorage';

interface PlayCell {
  deviceId: string;
  name: string;
  url: string;
  /** 播放中断时可回退的原始流地址（仅在播 AI 流且存在原始流时设置） */
  fallbackUrl?: string | null;
  playerEngine?: string | null;
  videoCodec?: string | null;
  playSources?: WvpPlaySourceOption[] | null;
  pendingRestore?: boolean;
  device?: MonitorTreeDeviceNode;
}

const { createMessage, createConfirm } = useMessage();

/** 勾选后点播 AI 流（检测框由算法任务烧录在此路流上） */
const enableAi = ref(false);

const splitScreenGuardApi: DashboardGuardTaskApi = {
  listAlgorithmTasks: (params) => listAlgorithmTasks(params, { errorMessageMode: 'none' }),
  listAvailableModels: () => getModelPage(
    { pageNo: 1, pageSize: 1000 },
    { errorMessageMode: 'none' },
  ),
  createAlgorithmTask: createAlgorithmTask as DashboardGuardTaskApi['createAlgorithmTask'],
  startAlgorithmTask: (taskId) => startAlgorithmTask(taskId, { errorMessageMode: 'none' }),
  stopAlgorithmTask: (taskId) => stopAlgorithmTask(taskId, { errorMessageMode: 'none' }),
};

function getSplitScreenAiDeviceId(device?: MonitorTreeDeviceNode | null) {
  const id = String(device?.id ?? '').trim();
  if (!id || id.startsWith('gb_ch_')) return '';
  return id;
}

function collectSplitScreenAiDevices(extraDevice?: MonitorTreeDeviceNode | null): MonitorTreeDeviceNode[] {
  const byId = new Map<string, MonitorTreeDeviceNode>();
  const addDevice = (device?: MonitorTreeDeviceNode | null) => {
    const id = getSplitScreenAiDeviceId(device);
    if (!id || byId.has(id)) return;
    byId.set(id, device as MonitorTreeDeviceNode);
  };

  state.playCells.forEach((cell) => {
    if (!cell?.deviceId || cell.deviceId.startsWith('gb_ch_')) return;
    const device = findMonitorDeviceById(treeData.value, cell.deviceId);
    addDevice(device ?? ({ id: cell.deviceId, name: cell.name } as MonitorTreeDeviceNode));
  });

  addDevice(extraDevice);
  return [...byId.values()];
}

function buildSplitScreenAiScope(devices: MonitorTreeDeviceNode[]): DashboardGuardScope | null {
  const deviceIds = [...new Set(devices.map(getSplitScreenAiDeviceId).filter(Boolean))];
  if (!deviceIds.length) return null;
  const sortedIds = [...deviceIds].sort();
  const firstDeviceName = formatCameraDeviceLabel(devices[0]) || devices[0]?.name || sortedIds[0];
  return {
    key: `split-screen-ai:${sortedIds.join(',')}`,
    label: deviceIds.length === 1 ? firstDeviceName : `分屏播放 ${deviceIds.length} 路`,
    deviceIds,
  };
}

async function ensureSplitScreenAiRecognitionForDevices(devices: MonitorTreeDeviceNode[]) {
  const scope = buildSplitScreenAiScope(devices);
  if (!scope) return true;
  try {
    await startDashboardGuardTask({ scope, api: splitScreenGuardApi });
    return true;
  } catch (error) {
    createMessage.error(`AI 识别启动失败：${extractDashboardGuardErrorMessage(error)}`);
    return false;
  }
}

async function ensureSplitScreenAiRecognitionForVisibleDevices() {
  return ensureSplitScreenAiRecognitionForDevices(collectSplitScreenAiDevices());
}

async function ensureGbSplitScreenAiRecognition(
  gb: { sipDeviceId: string; channelId: string },
  fallbackDevice?: MonitorTreeDeviceNode | null,
) {
  const aiDevice = await loadGbChannelSyncedDevice(gb.sipDeviceId, gb.channelId, fallbackDevice);
  if (!getSplitScreenAiDeviceId(aiDevice)) {
    createMessage.warning('国标通道尚未同步到设备库，无法启动 AI 识别；请先同步该通道');
    return { ready: false, device: fallbackDevice ?? null };
  }
  const ready = await ensureSplitScreenAiRecognitionForDevices(collectSplitScreenAiDevices(aiDevice));
  return { ready, device: aiDevice };
}

async function stopSplitScreenAiRecognitionForVisibleDevices() {
  const scope = buildSplitScreenAiScope(collectSplitScreenAiDevices());
  if (!scope) return;
  try {
    await stopDashboardGuardTask({ scope, api: splitScreenGuardApi });
  } catch (error) {
    console.warn('停止分屏 AI 识别任务失败:', error);
  }
}

const selectedKeys = ref<string[]>([]);
const expandedKeys = ref<string[]>([]);
const treeData = ref<TreeItem[]>([]);
const treeLoading = ref(false);
/** 有缓存时后台静默刷新 */
const treeRefreshing = ref(false);
const playerRefs = ref<any[]>([]);

const state = reactive({
  playCells: [] as (PlayCell | null)[],
  splitMode: 4,
  playerIdx: 0,
  isFull: false,
  loadingCells: [] as number[]});

const layoutPresets = ref<Record<number, CameraMonitorLayoutPreset>>({});
const activePresetId = ref<number | null>(null);
const isRestoringLayout = ref(false);
const presetPanelOpen = ref(false);

const currentLayout = computed(() => String(state.splitMode));
const currentCameraCount = computed(() => state.playCells.filter((c) => c?.deviceId).length);
const canSaveCurrentLayout = computed(() => currentCameraCount.value > 0);

function getPresetDisplayName(preset: CameraMonitorLayoutPreset | undefined, id: number) {
  if (!preset) return `方案 ${id}`;
  return preset.name?.trim() || `方案 ${id}`;
}

const activePresetSummary = computed(() => {
  if (!activePresetId.value) return '';
  const preset = layoutPresets.value[activePresetId.value];
  if (!preset) return `方案 ${activePresetId.value}`;
  const count = preset.slots.filter((s) => s.deviceId).length;
  return `${getPresetDisplayName(preset, activePresetId.value)} · ${count}路`;
});

function normalizeSplitMode(layout: string | number): number {
  const count = typeof layout === 'number' ? layout : parseInt(layout) || 1;
  if (count <= 1) return 1;
  if (count <= 4) return 4;
  if (count <= 9) return 9;
  return 16;
}

function initLayoutPresetsFromStorage() {
  const storage = loadCameraMonitorLayoutStorage();
  const presets: Record<number, CameraMonitorLayoutPreset> = {};
  for (const [key, preset] of Object.entries(storage.presets)) {
    const id = Number(key);
    if (id >= 1 && id <= MAX_CAMERA_MONITOR_LAYOUT_PRESETS) {
      presets[id] = preset;
    }
  }
  layoutPresets.value = presets;
  activePresetId.value = storage.activePresetId;
}

function persistLayoutPresets() {
  const presets: Record<string, CameraMonitorLayoutPreset> = {};
  for (const [key, preset] of Object.entries(layoutPresets.value)) {
    presets[String(key)] = preset;
  }
  saveCameraMonitorLayoutStorage({
    presets,
    activePresetId: activePresetId.value,
  });
}

function resolveDeviceForCell(cell: PlayCell): MonitorTreeDeviceNode | undefined {
  if (cell.device) return cell.device;
  const playId = cell.deviceId;
  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId);
    if (!gb) return undefined;
    return findMonitorGbDeviceByChannel(treeData.value, gb.sipDeviceId, gb.channelId) ?? undefined;
  }
  return findMonitorDeviceById(treeData.value, playId) ?? undefined;
}

function buildCurrentLayoutSlots(): CameraMonitorLayoutSlot[] {
  const slots: CameraMonitorLayoutSlot[] = [];
  for (let i = 0; i < state.splitMode; i++) {
    const cell = state.playCells[i];
    if (cell?.deviceId) {
      slots.push({
        deviceId: cell.deviceId,
        name: cell.name,
        device: serializeDeviceSnapshot(resolveDeviceForCell(cell)),
      });
    } else {
      slots.push({ deviceId: '', name: '' });
    }
  }
  return slots;
}

function saveToLayoutPreset(presetId: number, options?: { keepName?: boolean }) {
  if (!canSaveCurrentLayout.value) {
    createMessage.warning('当前没有已选摄像头，无法保存布局');
    return false;
  }

  const existing = layoutPresets.value[presetId];
  const preset: CameraMonitorLayoutPreset = {
    id: presetId,
    name: options?.keepName !== false && existing?.name ? existing.name : undefined,
    layout: currentLayout.value,
    enableAi: enableAi.value,
    slots: buildCurrentLayoutSlots(),
    updatedAt: Date.now(),
  };
  layoutPresets.value = { ...layoutPresets.value, [presetId]: preset };
  activePresetId.value = presetId;
  persistLayoutPresets();
  createMessage.success(`已保存到${getPresetDisplayName(preset, presetId)}`);
  return true;
}

function applyPresetStructure(preset: CameraMonitorLayoutPreset) {
  playerRefs.value.forEach((p) => p?.destroy?.());
  playerRefs.value = [];
  aiFallbackTimers.forEach((id) => window.clearTimeout(id));
  aiFallbackTimers.clear();

  state.splitMode = normalizeSplitMode(preset.layout);
  enableAi.value = preset.enableAi;
  state.playerIdx = 0;

  state.playCells = [];
  for (let i = 0; i < state.splitMode; i++) {
    const saved = preset.slots[i];
    if (saved?.deviceId) {
      state.playCells.push({
        deviceId: saved.deviceId,
        name: saved.name,
        url: '',
        pendingRestore: true,
        device: saved.device,
      });
    } else {
      state.playCells.push(null);
    }
  }
}

async function playSavedSlot(index: number, saved: CameraMonitorLayoutSlot) {
  const playId = saved.deviceId;
  if (!playId) return;

  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId);
    if (!gb) return;
    const synced =
      saved.device ?? findMonitorGbDeviceByChannel(treeData.value, gb.sipDeviceId, gb.channelId);
    const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolveGbChannelPlayUrls(
      gb.sipDeviceId,
      gb.channelId,
      { enableAi: enableAi.value, synced },
    );
    if (!url) {
      createMessage.warn(`方案恢复失败：${saved.name}`);
      state.playCells[index] = null;
      return;
    }
    await startPlayAtCell(index, {
      deviceId: playId,
      name: saved.name,
      url,
      fallbackUrl,
      preferAi,
      pendingAiUrl,
    });
    return;
  }

  const dev = saved.device ?? findMonitorDeviceById(treeData.value, playId);
  if (!dev) {
    createMessage.warn(`方案恢复失败：${saved.name}（缺少设备信息）`);
    state.playCells[index] = null;
    return;
  }
  const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolveDirectPlayUrl(dev);
  if (!url) {
    createMessage.warn(`方案恢复失败：${saved.name}`);
    state.playCells[index] = null;
    return;
  }
  await startPlayAtCell(index, {
    deviceId: playId,
    name: saved.name,
    url,
    fallbackUrl,
    preferAi,
    pendingAiUrl,
  });
}

async function restorePendingVideos() {
  if (isRestoringLayout.value) return;
  isRestoringLayout.value = true;
  try {
    const tasks: Promise<void>[] = [];
    state.playCells.forEach((cell, idx) => {
      if (!cell?.pendingRestore || !cell.deviceId) return;
      tasks.push(
        playSavedSlot(idx, {
          deviceId: cell.deviceId,
          name: cell.name,
          device: cell.device,
        }).finally(() => {
          const current = state.playCells[idx];
          if (current) {
            state.playCells[idx] = { ...current, pendingRestore: false };
          }
        }),
      );
    });
    await Promise.all(tasks);
  } finally {
    isRestoringLayout.value = false;
  }
}

async function activateLayoutPreset(presetId: number) {
  const preset = layoutPresets.value[presetId];
  if (!preset) return;

  applyPresetStructure(preset);
  activePresetId.value = presetId;
  persistLayoutPresets();
  await nextTick();
  await restorePendingVideos();
}

async function handleApplyPreset(presetId: number) {
  await activateLayoutPreset(presetId);
  presetPanelOpen.value = false;
}

function handleSavePreset(presetId: number) {
  saveToLayoutPreset(presetId);
}

function handleDeletePreset(presetId: number) {
  const preset = layoutPresets.value[presetId];
  if (!preset) return;
  const label = getPresetDisplayName(preset, presetId);
  createConfirm({
    iconType: 'warning',
    title: '删除布局方案',
    content: `确定删除「${label}」吗？删除后无法恢复。`,
    onOk: () => {
      const next = { ...layoutPresets.value };
      delete next[presetId];
      layoutPresets.value = next;
      if (activePresetId.value === presetId) {
        activePresetId.value = null;
      }
      persistLayoutPresets();
      createMessage.success('已删除布局方案');
    },
  });
}

initLayoutPresetsFromStorage();

const loadedCount = computed(() => state.playCells.filter((c) => c).length);

const gridStyle = computed((): CSSProperties => {
  const base: CSSProperties = {
    display: 'grid',
    gridTemplateColumns: `repeat(${Math.sqrt(state.splitMode)}, 1fr)`,
    gridTemplateRows: `repeat(${Math.sqrt(state.splitMode)}, 1fr)`,
    gap: '2px',
    width: '100%',
    height: '100%',
    padding: '2px'};
  if (state.isFull) {
    return { ...base, position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, zIndex: 9999 };
  }
  return base;
});

const setPlayerRef = (el: any, index: number) => {
  if (el) playerRefs.value[index] = el;
};

function triggerCellFillResize(cellIdx: number) {
  const player = playerRefs.value[cellIdx];
  if (!player?.jessibuca) return;
  const run = () => {
    const inst = playerRefs.value[cellIdx]?.jessibuca;
    if (!inst || !playerRefs.value[cellIdx]?.playing) return;
    try {
      inst.setScaleMode?.(0);
      inst.resize?.();
    } catch {
      /* 播放器尚未就绪 */
    }
  };
  run();
  window.setTimeout(run, 300);
  window.setTimeout(run, 800);
}

function triggerAllCellFillResize() {
  state.playCells.forEach((cell, idx) => {
    if (cell) triggerCellFillResize(idx);
  });
}

async function resolveDirectPlayUrl(device: MonitorTreeDeviceNode) {
  if (isGb28181Device(device.source, device.device_kind)) {
    return { url: null as string | null, fallbackUrl: null as string | null | undefined };
  }
  return pickDirectPlayUrls(device, enableAi.value);
}

const aiFallbackTimers = new Map<number, number>();
const aiFallbackPlayingUrls = new Map<number, string>();
function clearAiFallbackTimer(cellIdx: number) {
  const timerId = aiFallbackTimers.get(cellIdx);
  if (timerId != null) {
    window.clearTimeout(timerId);
    aiFallbackTimers.delete(cellIdx);
  }
}

function handleCellPlaying(cellIdx: number, playUrl?: string) {
  const cell = state.playCells[cellIdx];
  const currentUrl = cell?.url?.trim();
  const reportedUrl = playUrl?.trim();
  if (!currentUrl || (reportedUrl && reportedUrl !== currentUrl)) return;
  aiFallbackPlayingUrls.set(cellIdx, currentUrl);
  clearAiFallbackTimer(cellIdx);
}

/** 首帧已播原始流后，后台探测 AI 就绪再无缝切换 */
function schedulePendingAiUpgrade(
  cellIdx: number,
  deviceId: string,
  aiUrl: string,
  fallbackUrl: string,
) {
  schedulePendingAiStreamUpgrade(
    aiUrl,
    fallbackUrl,
    () => {
      const cell = state.playCells[cellIdx];
      return !!cell && cell.deviceId === deviceId && cell.url !== aiUrl;
    },
    () => {
      const cell = state.playCells[cellIdx];
      if (!cell) return;
      state.playCells[cellIdx] = { ...cell, url: aiUrl, fallbackUrl };
    },
  );
}


/** 优先当前通道，否则第一个空通道，否则覆盖当前通道 */
function resolveTargetCellIndex(): number {
  if (!state.playCells[state.playerIdx]) {
    return state.playerIdx;
  }
  const emptyIdx = state.playCells.findIndex((c) => !c);
  if (emptyIdx >= 0) return emptyIdx;
  return state.playerIdx;
}

async function startPlayAtCell(
  cellIdx: number,
  payload: {
    deviceId: string;
    name: string;
    url: string;
    fallbackUrl?: string | null;
    preferAi?: boolean;
    playerEngine?: string | null;
    videoCodec?: string | null;
    playSources?: WvpPlaySourceOption[] | null;
    pendingAiUrl?: string | null;
  },
) {
  clearAiFallbackTimer(cellIdx);
  aiFallbackPlayingUrls.delete(cellIdx);
  const existing = state.playCells[cellIdx];
  if (existing && playerRefs.value[cellIdx]?.destroy) {
    playerRefs.value[cellIdx].destroy();
  }

  const fallbackUrl = payload.fallbackUrl?.trim();
  const hasFallback = !!(payload.preferAi && fallbackUrl && fallbackUrl !== payload.url);
  const playSources = payload.playSources?.filter((source) => source.url?.trim()) ?? null;
  const pendingAi = payload.pendingAiUrl?.trim();

  state.playerIdx = cellIdx;
  state.playCells[cellIdx] = {
    deviceId: payload.deviceId,
    name: payload.name,
    url: payload.url,
    fallbackUrl: hasFallback ? fallbackUrl : null,
    playerEngine: payload.playerEngine ?? null,
    videoCodec: payload.videoCodec ?? null,
    playSources};

  await nextTick();

  if (pendingAi && pendingAi !== payload.url) {
    schedulePendingAiUpgrade(cellIdx, payload.deviceId, pendingAi, payload.url);
  }

  if (!hasFallback) return;

  const primaryUrl = payload.url;
  const timerId = window.setTimeout(async () => {
    aiFallbackTimers.delete(cellIdx);
    const cell = state.playCells[cellIdx];
    if (!cell || cell.url !== primaryUrl) return;
    if (aiFallbackPlayingUrls.get(cellIdx) === primaryUrl) return;
    if (playerRefs.value[cellIdx]?.playing) return;

    createMessage.warning(
      'AI 流暂不可用（请确认算法任务已启动且媒体服务器已收到 AI 推流），已切换为原始画面（无检测框）',
    );
    state.playCells[cellIdx] = { ...cell, url: fallbackUrl, fallbackUrl: null };
  }, AI_PLAY_FALLBACK_MS);
  aiFallbackTimers.set(cellIdx, timerId);
}

function findNextPlaySource(cell: PlayCell): WvpPlaySourceOption | null {
  const sources = cell.playSources?.filter((source) => source.url?.trim()) ?? [];
  if (!sources.length) return null;
  const currentUrl = cell.url.trim();
  const currentIndex = sources.findIndex((source) => source.url.trim() === currentUrl);
  return sources.find((_, index) => index > currentIndex) ?? null;
}

/** 播放中断（timeout/error）：AI 流先回退原始流，原始流失败继续尝试备用播放源 */
function handleCellStreamError(cellIdx: number) {
  const cell = state.playCells[cellIdx];
  if (!cell) return;
  const fb = cell.fallbackUrl?.trim();
  clearAiFallbackTimer(cellIdx);
  aiFallbackPlayingUrls.delete(cellIdx);
  if (fb && fb !== cell.url) {
    createMessage.warning('AI 流已中断，已切换为原始画面（无检测框）');
    state.playCells[cellIdx] = { ...cell, url: fb, fallbackUrl: null };
    nextTick(() => playerRefs.value[cellIdx]?.play?.());
    return;
  }

  const nextSource = findNextPlaySource(cell);
  if (!nextSource) return;
  createMessage.warning('当前视频流播放失败，正在尝试备用播放源');
  state.playCells[cellIdx] = {
    ...cell,
    url: nextSource.url,
    fallbackUrl: null,
    playerEngine: nextSource.playerEngine,
    videoCodec: nextSource.videoCodec,
  };
  nextTick(() => playerRefs.value[cellIdx]?.play?.());
}

async function reloadPlayCellAtIndex(cellIdx: number) {
  const cell = state.playCells[cellIdx];
  if (!cell) return;

  const playId = cell.deviceId;
  if (playId.startsWith('gb_ch_')) {
    const gb = parseGbChannelKey(playId);
    if (gb) {
      let synced = findMonitorGbDeviceByChannel(treeData.value, gb.sipDeviceId, gb.channelId);
      if (enableAi.value) {
        const recognition = await ensureGbSplitScreenAiRecognition(gb, synced);
        synced = recognition.device ?? synced;
        if (!recognition.ready) {
          enableAi.value = false;
        }
      }
      const { url, fallbackUrl, preferAi, pendingAiUrl, playerEngine, videoCodec, playSources } = await resolveGbChannelPlayUrls(
        gb.sipDeviceId,
        gb.channelId,
        { enableAi: enableAi.value, synced },
      );
      if (url) {
        await startPlayAtCell(cellIdx, {
          deviceId: playId,
          name: cell.name,
          url,
          fallbackUrl,
          preferAi,
          playerEngine,
          videoCodec,
          playSources,
          pendingAiUrl});
      }
    }
    return;
  }

  const device = findMonitorDeviceById(treeData.value, playId);
  if (!device) return;

  if (enableAi.value) {
    const recognitionReady = await ensureSplitScreenAiRecognitionForDevices(collectSplitScreenAiDevices(device));
    if (!recognitionReady) {
      enableAi.value = false;
    }
  }

  const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolveDirectPlayUrl(device);
  if (!url) {
    createMessage.warn(enableAi.value ? '该设备暂无 AI 流或原始流地址' : '该设备暂无播放地址');
    return;
  }
  await startPlayAtCell(cellIdx, {
    deviceId: playId,
    name: cell.name,
    url,
    fallbackUrl,
    preferAi,
    pendingAiUrl});
}

async function reloadAllPlayCellsForAiToggle() {
  const tasks: Promise<void>[] = [];
  state.playCells.forEach((cell, idx) => {
    if (cell) tasks.push(reloadPlayCellAtIndex(idx));
  });
  await Promise.all(tasks);
}

let aiToggleRequestSeq = 0;

watch(enableAi, async (checked) => {
  const requestSeq = ++aiToggleRequestSeq;
  if (checked) {
    const recognitionReady = await ensureSplitScreenAiRecognitionForVisibleDevices();
    if (requestSeq !== aiToggleRequestSeq) return;
    if (!recognitionReady) {
      enableAi.value = false;
      return;
    }
  } else {
    await stopSplitScreenAiRecognitionForVisibleDevices();
    if (requestSeq !== aiToggleRequestSeq) return;
  }
  reloadAllPlayCellsForAiToggle();
});

const onLoadGbDeviceChannels: TreeProps['loadData'] = (treeNode) => {
  return new Promise<void>((resolve) => {
    const key = String(treeNode?.key ?? treeNode?.eventKey ?? '');
    if (!key.startsWith('gb_dev_')) {
      resolve();
      return;
    }
    const sipDeviceId = key.slice('gb_dev_'.length);
    const dataRef = (treeNode.dataRef ?? treeNode) as TreeItem;
    if (dataRef?.children?.length) {
      resolve();
      return;
    }

    getDeviceChannels(sipDeviceId)
      .then((res) => {
        const list = res.data || res.list || [];
        dataRef.children = enrichWvpChannelTreeNodes(
          buildWvpChannelTreeNodes(list, sipDeviceId),
          treeData.value,
        );
        dataRef.isLeaf = !dataRef.children?.length;
        treeData.value = [...treeData.value];
        if (!expandedKeys.value.includes(key)) {
          expandedKeys.value = [...expandedKeys.value, key];
        }
        resolve();
      })
      .catch(() => resolve());
  });
};

function applyMonitorTreeBundle(bundle: MonitorDirectoryTreeBundle) {
  treeData.value = bundle.treeItems;
  expandedKeys.value = collectMonitorTreeExpandedKeys(treeData.value);
}

/** 缓存优先加载；国标通道展开时再按需请求 WVP */
async function loadMonitorTree(options?: { force?: boolean }) {
  const hasCache = !options?.force && !!getCachedMonitorDirectoryTreeBundle()?.treeItems?.length;
  if (!hasCache) treeLoading.value = true;
  await loadMonitorDirectoryTreeWithCache({
    force: options?.force,
    skipSync: true,
    onBundle: (bundle) => {
      applyMonitorTreeBundle(bundle);
    },
    onRefreshingChange: (v) => {
      treeRefreshing.value = v;
      if (hasCache) return;
      if (!v) treeLoading.value = false;
    },
    onError: (e) => {
      if (!treeData.value.length) {
        console.error(e);
        createMessage.error('加载设备目录树失败');
        treeData.value = [];
      }
      treeLoading.value = false;
    }});
  if (!hasCache) treeLoading.value = false;
}

/** 同步国标设备并刷新目录树 */
async function handleRefresh() {
  try {
    treeLoading.value = true;
    invalidateMonitorDirectoryTreeCache();
    try {
      const { channels } = await collectWvpGbChannelsForSync();
      const payload = await syncGb28181Devices(channels);
      const created = payload?.created ?? 0;
      const total = payload?.total_gb_devices ?? 0;
      const wvpCount = payload?.wvp_device_count ?? 0;
      if (wvpCount > 0 && total === 0) {
        createMessage.warning(
          `WVP 有 ${wvpCount} 个国标设备，但未入库；请检查 VIDEO 服务与数据库`,
        );
      } else if (wvpCount === 0) {
        createMessage.warning('未从 WVP 拉取到国标设备，请检查 dev-api/gb28181 网关');
      } else {
        createMessage.success(`同步完成：新增 ${created} 个，共 ${total} 个国标设备`);
      }
    } catch (e) {
      console.error(e);
      createMessage.error('同步国标设备失败，请检查 WVP 服务与网络');
    }
    await loadMonitorTree({ force: true });
  } catch (e) {
    console.error(e);
    createMessage.error('加载设备目录树失败');
    if (!treeData.value.length) treeData.value = [];
  } finally {
    treeLoading.value = false;
  }
}

async function playGbChannel(cellIdx: number, gb: GbChannelRef) {
  const playId = `gb_ch_${gb.sipDeviceId},${gb.channelId}`;
  const duplicate = state.playCells.findIndex((c) => c?.deviceId === playId);
  if (duplicate >= 0 && duplicate !== state.playerIdx) {
    state.playerIdx = duplicate;
    createMessage.info('该通道已在播放，已切换到对应窗口');
    return;
  }

  if (state.loadingCells.includes(cellIdx)) return;
  state.loadingCells.push(cellIdx);

  try {
    const displayName = resolveMonitorGbChannelDisplayName(
      gb.sipDeviceId,
      gb.channelId,
      treeData.value,
      gb.name,
    );
    const node = findMonitorTreeNodeByKey(treeData.value, `gb_ch_${gb.sipDeviceId},${gb.channelId}`);
    const synced =
      findMonitorGbDeviceByChannel(treeData.value, gb.sipDeviceId, gb.channelId) ??
      ((node as any)?.device as MonitorTreeDeviceNode | undefined);
    let playDevice: MonitorTreeDeviceNode | null | undefined = synced;
    if (enableAi.value) {
      const recognition = await ensureGbSplitScreenAiRecognition(gb, synced);
      playDevice = recognition.device ?? synced;
      if (!recognition.ready) {
        enableAi.value = false;
      }
    }
    const { url, fallbackUrl, preferAi, pendingAiUrl, playerEngine, videoCodec, playSources } = await resolveGbChannelPlayUrls(
      gb.sipDeviceId,
      gb.channelId,
      { enableAi: enableAi.value, synced: playDevice },
    );
    if (!url) {
      createMessage.warn(
        enableAi.value
          ? '国标通道 AI 流不可用，请确认算法任务已启动；WVP 点播也失败，请检查通道状态'
          : '国标通道拉流失败，请检查 WVP 服务与通道状态',
      );
      return;
    }

    await startPlayAtCell(cellIdx, {
      deviceId: playId,
      name: displayName,
      url,
      fallbackUrl,
      preferAi,
      playerEngine,
      videoCodec,
      playSources,
      pendingAiUrl});
  } catch (e) {
    console.error(e);
    createMessage.error('播放失败，请检查设备连接');
  } finally {
    const i = state.loadingCells.indexOf(cellIdx);
    if (i > -1) state.loadingCells.splice(i, 1);
  }
}

async function handleTreeSelect(keys: string[] | string) {
  const keyList = Array.isArray(keys) ? keys : keys ? [keys] : [];
  if (!keyList.length) return;
  const key = keyList[0];

  if (String(key).startsWith('gb_dev_')) {
    createMessage.info('请展开国标设备并选择具体通道');
    return;
  }

  if (String(key).startsWith('nvr_')) {
    createMessage.info('请展开 NVR 并选择具体通道');
    return;
  }

  if (String(key).startsWith('gb_dir_') || String(key).startsWith('dir_')) {
    createMessage.info('请选择目录下的摄像头或通道');
    return;
  }

  const cellIdx = resolveTargetCellIndex();

  if (String(key).startsWith('gb_ch_')) {
    let gb = parseGbChannelKey(key);
    const node = findMonitorTreeNodeByKey(treeData.value, key);
    if (node && (node as any).gbChannel) {
      gb = (node as any).gbChannel as GbChannelRef;
    }
    if (!gb) {
      createMessage.warn('无效国标通道');
      return;
    }
    await playGbChannel(cellIdx, gb);
    return;
  }

  if (!String(key).startsWith('device_')) {
    createMessage.info('请选择摄像头或国标通道');
    return;
  }

  const node = findMonitorTreeNodeByKey(treeData.value, key);
  const device = (node as any)?.device as MonitorTreeDeviceNode | undefined;
  if (!device) {
    createMessage.warn('无效设备');
    return;
  }
  if (isGb28181Device(device.source, device.device_kind)) {
    createMessage.info('请展开上级国标设备并选择通道');
    return;
  }

  const duplicate = state.playCells.findIndex((c) => c?.deviceId === device.id);
  if (duplicate >= 0 && duplicate !== state.playerIdx) {
    state.playerIdx = duplicate;
    createMessage.info('该设备已在播放，已切换到对应通道');
    return;
  }

  if (state.loadingCells.includes(cellIdx)) return;
  state.loadingCells.push(cellIdx);

  try {
    if (enableAi.value) {
      const recognitionReady = await ensureSplitScreenAiRecognitionForDevices(collectSplitScreenAiDevices(device));
      if (!recognitionReady) {
        enableAi.value = false;
      }
    }
    const { url, fallbackUrl, preferAi, pendingAiUrl } = await resolveDirectPlayUrl(device);
    if (!url) {
      createMessage.warn(
        enableAi.value
          ? '该设备暂无 AI 流或原始流播放地址，请先在设备列表中配置'
          : '该设备暂无可用播放地址，请先在设备列表中配置流地址',
      );
      return;
    }
    await startPlayAtCell(cellIdx, {
      deviceId: device.id,
      name: formatCameraDeviceLabel(device),
      url,
      fallbackUrl,
      preferAi,
      pendingAiUrl});
  } catch (e) {
    console.error(e);
    createMessage.error('播放失败，请检查设备连接');
  } finally {
    const i = state.loadingCells.indexOf(cellIdx);
    if (i > -1) state.loadingCells.splice(i, 1);
  }
}

function handleSplitModeChange() {
  const n = state.splitMode;
  if (state.playCells.length > n) {
    for (let i = n; i < state.playCells.length; i++) {
      if (playerRefs.value[i]?.destroy) playerRefs.value[i].destroy();
    }
    state.playCells = state.playCells.slice(0, n);
  } else {
    while (state.playCells.length < n) state.playCells.push(null);
  }
  if (state.playerIdx >= n) state.playerIdx = 0;
  nextTick(() => triggerAllCellFillResize());
}

function handleGridDelete() {
  const idx = state.playerIdx;
  if (!state.playCells[idx]) {
    createMessage.warn('当前通道没有视频');
    return;
  }
  if (playerRefs.value[idx]?.destroy) playerRefs.value[idx].destroy();
  state.playCells[idx] = null;
  createMessage.success('已删除选中通道');
}

function handleCellDelete(index: number) {
  if (!state.playCells[index]) return;
  if (playerRefs.value[index]?.destroy) playerRefs.value[index].destroy();
  state.playCells[index] = null;
}

function handleClearAll() {
  playerRefs.value.forEach((p, i) => {
    if (p?.destroy && state.playCells[i]) p.destroy();
  });
  state.playCells = Array(state.splitMode).fill(null);
  state.playerIdx = 0;
  createMessage.success('已清空所有通道');
}

function handleGridFull() {
  state.isFull = !state.isFull;
  if (state.isFull) document.documentElement.requestFullscreen?.();
  else document.exitFullscreen?.();
}

const handleFullscreenChange = () => {
  state.isFull = !!document.fullscreenElement;
  nextTick(() => triggerAllCellFillResize());
};

onMounted(async () => {
  if (activePresetId.value && layoutPresets.value[activePresetId.value]) {
    applyPresetStructure(layoutPresets.value[activePresetId.value]);
  } else {
    state.playCells = Array(state.splitMode).fill(null);
  }
  await loadMonitorTree();
  if (state.playCells.some((c) => c?.pendingRestore)) {
    await restorePendingVideos();
  }
  document.addEventListener('fullscreenchange', handleFullscreenChange);
});

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  aiFallbackTimers.forEach((id) => window.clearTimeout(id));
  aiFallbackTimers.clear();
  aiFallbackPlayingUrls.clear();
  playerRefs.value.forEach((p) => p?.destroy?.());
});

defineExpose({ refresh: () => loadMonitorTree(), forceRefresh: handleRefresh });
</script>

<style lang="less" scoped>
.split-screen-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  overflow: hidden;

  &.fullscreen-mode {
    position: fixed;
    inset: 0;
    z-index: 9999;
    height: 100vh;
    max-height: 100vh;
  }
}

.monitor-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  background: #fff;
  overflow: hidden;

  :deep(.ant-layout) {
    height: 100%;
  }
}

.monitor-content-layout {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  position: relative;
}

.device-tree-sider {
  height: 100%;
  overflow: hidden;
  border-right: 1px solid #e5e7eb;

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .tree-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;

    :deep(.xingyuv-collapse-container__header) {
      height: auto;
      min-height: 48px;
      padding: 0 12px !important;
      background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
      border-bottom: 1px solid #eef0f3;
      border-radius: 0;
    }

    :deep(.xingyuv-basic-title) {
      flex: 1;
      min-width: 0;
      padding-left: 0 !important;
      font-size: inherit;
      font-weight: inherit;
      line-height: inherit;
      cursor: default;
      user-select: none;
    }

    :deep(.xingyuv-collapse-container__action) {
      flex: none;
    }

    /* 仅让折叠内容区占满剩余高度，不改变标题栏样式 */
    :deep(> .p-2) {
      flex: 1;
      min-height: 0;
      overflow: hidden;
      display: flex;
      flex-direction: column;

      > * {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    :deep(.xingyuv-collapse-container__body) {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }
}

.tree-header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    background: #eff6ff;
    color: #3b82f6;
    flex-shrink: 0;
  }

  &__text {
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    line-height: 1;
    letter-spacing: 0.01em;
  }
}

.tree-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tree-scroll {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 12px;
  display: flex;
  flex-direction: column;

  :deep(.device-tree) {
    flex: 1;
    min-height: 0;
    height: 100%;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    .ant-spin-nested-loading,
    .ant-spin-container {
      flex: 1;
      min-height: 0;
      height: 100%;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .scroll-container {
      flex: 1;
      min-height: 0;
    }

    .ant-tree-switcher {
      width: 16px;
      margin-inline-end: 2px;
    }

    /* 叶子节点前的展开占位更窄，避免整体偏右 */
    .ant-tree-switcher-noop {
      width: 8px;
    }

    .ant-tree-node-content-wrapper {
      padding-inline: 2px 6px;
      min-height: 26px;
      line-height: 26px;
    }

    .ant-tree-title,
    [class*='-tree__title'] {
      padding-left: 0 !important;
      padding-right: 8px;
    }
  }
}

.toolbar-header {
  position: relative;
  height: auto;
  min-height: 50px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  line-height: normal;
  z-index: 30;

  &.panel-open {
    z-index: 210;
    border-bottom-color: transparent;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  }
}

.toolbar-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  width: 100%;
}

.header-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.toolbar-divider {
  height: 20px;
  margin: 0;
  border-color: #e5e7eb;
}

.toolbar-section {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.toolbar-status {
  margin-left: auto;

  .status-text {
    color: #6b7280;
    font-size: 13px;
  }
}

.layout-preset-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  flex-shrink: 0;
  white-space: nowrap;

  &:hover {
    border-color: #93c5fd;
    background: #eff6ff;
    color: #1d4ed8;
  }

  &.open {
    border-color: #3b82f6;
    background: #eff6ff;
    color: #1d4ed8;
  }

  &.has-active {
    border-color: #93c5fd;
    background: #eff6ff;
  }

  .trigger-label {
    font-weight: 500;
    white-space: nowrap;
  }

  .trigger-badge {
    max-width: 140px;
    padding: 0 6px;
    height: 20px;
    line-height: 20px;
    border-radius: 10px;
    font-size: 11px;
    color: #1e40af;
    background: #dbeafe;
    border: 1px solid #93c5fd;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.preset-panel-backdrop {
  position: absolute;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.15);
}

.split-screen-container.preset-panel-open {
  .monitor-content-layout {
    position: relative;
  }
}

.video-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #f3f4f6;
}

.video-grid {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.video-cell {
  position: relative;
  border: 2px solid #e5e7eb;
  background: #111;
  cursor: pointer;
  overflow: hidden;

  &.cell-selected {
    border-color: #3b82f6;
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.35);
  }

  &.cell-empty {
    background: #f9fafb;
    border-style: dashed;
  }

  &.cell-loading::after {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.65);
    z-index: 5;
  }
}

.empty-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  gap: 8px;

  .empty-icon {
    font-size: 36px;
    color: #9ca3af;
  }

  .empty-hint {
    font-size: 12px;
  }
}

.restoring-cell {
  background: #111;

  .spinning {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.player-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 120px;
  overflow: hidden;

  :deep(.jessibuca-root) {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  :deep(.jessibuca-container) {
    width: 100% !important;
    height: 100% !important;
  }

  &:hover .cell-close-btn {
    opacity: 0.85;
  }
}

.cell-name {
  position: absolute;
  left: 6px;
  /* 紧贴播放栏上方（工具栏约 28px） */
  bottom: 25px;
  z-index: 11;
  max-width: calc(100% - 36px);
  padding: 1px 5px;
  font-size: 11px;
  line-height: 1.25;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(0, 0, 0, 0.28);
  border-radius: 2px;
  pointer-events: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-close-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 3;
  min-width: 22px;
  height: 22px;
  padding: 0;
  opacity: 0.45;
  color: #fff !important;
  background: rgba(0, 0, 0, 0.25) !important;
  border-radius: 2px;
  transition: opacity 0.2s;

  &:hover {
    opacity: 1 !important;
    background: rgba(239, 68, 68, 0.75) !important;
  }
}
</style>
