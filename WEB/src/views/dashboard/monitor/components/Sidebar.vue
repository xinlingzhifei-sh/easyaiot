<template>
  <div class="monitor-sidebar" data-testid="monitor-sidebar">
    <!-- 设备目录 -->
    <div class="sidebar-section directory-section">
      <div class="section-header">
        <Icon icon="ant-design:apartment-outlined" :size="17" class="header-icon" />
        <div class="section-heading">
          <span class="section-kicker">DEVICE DIRECTORY</span>
          <span class="section-title">设备导航</span>
        </div>
        <div class="header-actions">
          <span class="device-count" v-if="!loading && treeData.length > 0">
            {{ playableLeafCount }} 个通道
          </span>
        </div>
      </div>
      <!-- 设备树（与分屏监控一致：搜索框固定，树列表区域滚动） -->
      <div class="sidebar-tree">
        <div class="sidebar-tree-scroll">
          <BasicTree
            class="sidebar-device-tree"
            :tree-data="treeData"
            :expanded-keys="expandedKeys"
            :selected-keys="selectedKeys"
            :loading="loading"
            search
            :showIcon="true"
            :indent="12"
            :click-row-to-expand="false"
            :load-data="onLoadGbDeviceChannels"
            @update:expanded-keys="handleExpandedKeysChange"
            @select="handleTreeSelect"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, onMounted } from 'vue'
import { Icon } from '@/components/Icon'
import { BasicTree } from '@/components/Tree'
import type { TreeItem } from '@/components/Tree'
import type { MonitorTreeDeviceNode } from '@/api/device/camera'
import { formatCameraDeviceLabel, isGb28181Device } from '@/views/camera/utils/deviceLabel'
import {
  collectMonitorTreeExpandedKeys,
  countMonitorTreePlayableLeaves,
  findMonitorGbDeviceByChannel,
  findMonitorTreeNodeByKey,
} from '@/views/camera/utils/monitorDeviceTree'
import { buildWvpChannelTreeNodes, parseGbChannelKey, type GbChannelRef } from '@/views/camera/utils/gb28181Tree'
import { getDeviceChannels } from '@/api/device/gb28181'
import { getCachedMonitorDirectoryTreeBundle, invalidateMonitorDirectoryTreeCache } from '@/views/camera/utils/monitorDirectoryTreeCache'
import { loadMonitorDirectoryTreeWithCache } from '@/views/camera/utils/monitorDirectoryTreeLoad'
import { syncGb28181DevicesInBackground } from '@/views/camera/utils/wvpGbSync'
import { isGb28181Enabled } from '@/utils/deployProfile'
import {
  enrichWvpChannelTreeNodes,
  resolveMonitorGbChannelDisplayName,
} from '@/views/camera/utils/monitorGbDisplay'
import { useMessage } from '@/hooks/web/useMessage'
import type { TreeProps } from 'ant-design-vue'

defineOptions({
  name: 'MonitorSidebar'
})

defineProps<{
  selectedDevice?: any
}>()

const emit = defineEmits<{
  (e: 'device-change', device: any): void
  (e: 'device-play', device: any): void
}>()

const {createMessage} = useMessage()

const expandedKeys = ref<string[]>([])
const selectedKeys = ref<string[]>([])
const treeData = ref<TreeItem[]>([])
const loading = ref(false)

const playableLeafCount = computed(() => countMonitorTreePlayableLeaves(treeData.value))

const onLoadGbDeviceChannels: TreeProps['loadData'] = (treeNode) => {
  return new Promise<void>((resolve) => {
    const key = String(treeNode?.key ?? treeNode?.eventKey ?? '')
    if (!key.startsWith('gb_dev_')) {
      resolve()
      return
    }
    const sipDeviceId = key.slice('gb_dev_'.length)
    const dataRef = (treeNode.dataRef ?? treeNode) as TreeItem
    if (dataRef?.children?.length) {
      resolve()
      return
    }

    getDeviceChannels(sipDeviceId)
      .then((res) => {
        const list = res.data || res.list || []
        dataRef.children = enrichWvpChannelTreeNodes(
          buildWvpChannelTreeNodes(list, sipDeviceId),
          treeData.value,
        )
        dataRef.isLeaf = !dataRef.children?.length
        treeData.value = [...treeData.value]
        if (!expandedKeys.value.includes(key)) {
          expandedKeys.value = [...expandedKeys.value, key]
        }
        resolve()
      })
      .catch(() => resolve())
  })
}

const applyMonitorTreeBundle = (bundle: { treeItems: TreeItem[] }) => {
  treeData.value = bundle.treeItems
  expandedKeys.value = collectMonitorTreeExpandedKeys(treeData.value)
}

const loadTreeData = async (options?: { force?: boolean }) => {
  const hasCache = !!getCachedMonitorDirectoryTreeBundle()?.treeItems?.length
  if (!hasCache) loading.value = true

  await loadMonitorDirectoryTreeWithCache({
    force: options?.force,
    skipSync: false,
    onBundle: (bundle) => {
      applyMonitorTreeBundle(bundle)
    },
    onError: (error) => {
      console.error('加载设备目录失败', error)
      if (!treeData.value.length) {
        createMessage.error('加载设备目录失败: ' + (error as Error)?.message)
        treeData.value = []
      }
    },
    onRefreshingChange: (v) => {
      if (!treeData.value.length) loading.value = v
    },
  })
  loading.value = false
}

/** 首页进入时后台同步 WVP 国标设备，避免必须先打开分屏监控点「刷新」 */
const syncGbDevicesInBackground = async () => {
  if (!isGb28181Enabled()) return
  const { created } = await syncGb28181DevicesInBackground()
  if (created > 0) {
    invalidateMonitorDirectoryTreeCache()
    await loadTreeData({ force: true })
  }
}

// 处理展开/收起变化
const handleExpandedKeysChange = (keys: string[]) => {
  expandedKeys.value = keys
}

function buildGbChannelPlayPayload(gb: GbChannelRef, node: TreeItem | null) {
  const playId = `gb_ch_${gb.sipDeviceId},${gb.channelId}`
  const synced =
    findMonitorGbDeviceByChannel(treeData.value, gb.sipDeviceId, gb.channelId) ??
    ((node as TreeItem & { device?: MonitorTreeDeviceNode })?.device ?? null)
  const displayName = resolveMonitorGbChannelDisplayName(
    gb.sipDeviceId,
    gb.channelId,
    treeData.value,
    gb.name,
  )
  const monitorDevice: MonitorTreeDeviceNode = synced ?? {
    type: 'device',
    id: playId,
    name: displayName.replace(/^\[GB28181\]\s*/, '').trim() || gb.name,
    source: `gb28181://${gb.sipDeviceId}/${gb.channelId}`,
    device_kind: 'gb28181',
  }
  return {
    id: playId,
    name: displayName,
    location: node ? getFullPath(node, treeData.value) : '',
    device: monitorDevice,
  }
}

// 处理树节点选择（与分屏监控一致：国标设备下展开通道后点播）
const handleTreeSelect = async (keys: string[], _info?: unknown) => {
  if (!keys.length) return

  const selectedKey = String(keys[0])
  const node = findMonitorTreeNodeByKey(treeData.value, selectedKey)
  if (!node) return

  if (selectedKey.startsWith('gb_dev_')) {
    selectedKeys.value = [selectedKey]
    if (!node.children?.length) {
      createMessage.info('请展开国标设备后选择通道播放')
    }
    return
  }
  if (selectedKey.startsWith('nvr_')) {
    selectedKeys.value = [selectedKey]
    if (!node.children?.length) {
      createMessage.info('请展开 NVR 后选择通道播放')
    }
    return
  }
  if (selectedKey.startsWith('gb_dir_') || selectedKey.startsWith('dir_')) {
    selectedKeys.value = [selectedKey]
    return
  }

  if (selectedKey.startsWith('gb_ch_')) {
    let gb = parseGbChannelKey(selectedKey)
    if ((node as any).gbChannel) {
      gb = (node as any).gbChannel as GbChannelRef
    }
    if (!gb) {
      createMessage.warning('无效国标通道')
      return
    }
    selectedKeys.value = [selectedKey]
    const payload = buildGbChannelPlayPayload(gb, node)
    emit('device-change', payload)
    emit('device-play', payload)
    return
  }

  if (!selectedKey.startsWith('device_')) {
    createMessage.info('请选择摄像头或国标通道')
    return
  }

  const device = (node as any).device as MonitorTreeDeviceNode | undefined
  if (!device) {
    createMessage.warning('无效设备')
    return
  }
  if (isGb28181Device(device.source, device.device_kind)) {
    createMessage.info('请展开上级国标设备并选择通道')
    return
  }

  selectedKeys.value = [selectedKey]
  const payload = {
    id: device.id,
    name: formatCameraDeviceLabel(device),
    location: getFullPath(node, treeData.value),
    device,
  }
  emit('device-change', payload)
  emit('device-play', {
    ...payload,
    http_stream: device.http_stream,
    rtmp_stream: device.rtmp_stream,
    ai_http_stream: device.ai_http_stream,
    ai_rtmp_stream: device.ai_rtmp_stream,
    source: device.source,
  })
}

// 获取完整路径
const getFullPath = (node: TreeItem, treeNodes: TreeItem[]): string => {
  // 递归查找父节点路径
  const findPath = (nodes: TreeItem[], targetKey: string, currentPath: string[] = []): string[] | null => {
    for (const n of nodes) {
      const newPath = [...currentPath, n.title as string]
      if (n.key === targetKey) {
        return newPath
      }
      if (n.children && n.children.length > 0) {
        const found = findPath(n.children as TreeItem[], targetKey, newPath)
        if (found) {
          return found
        }
      }
    }
    return null
  }

  const fullPath = findPath(treeNodes, node.key as string)
  return fullPath ? fullPath.join(' / ') : (node.title as string)
}

// 组件挂载时加载数据
onMounted(() => {
  loadTreeData()
  syncGbDevicesInBackground()
})
</script>

<style lang="less" scoped>
.monitor-sidebar {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
}

.sidebar-section {
  background: var(--dashboard-panel);
  border-radius: var(--dashboard-radius);
  border: 1px solid var(--dashboard-border);
  box-shadow: none;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &::before {
    display: none;
  }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 58px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--dashboard-border);
  background: var(--dashboard-panel-strong);
  position: relative;
  z-index: 1;

  .header-icon {
    color: var(--dashboard-cyan);
    filter: none;
  }

  .section-heading {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .section-kicker {
    color: var(--dashboard-weak);
    font-family: 'IBM Plex Mono', Consolas, monospace;
    font-size: 8px;
    letter-spacing: 0.1em;
  }

  .section-title {
    color: var(--dashboard-text);
    font-size: 14px;
    font-weight: 600;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 8px;

    .device-count {
      font-size: 11px;
      color: var(--dashboard-muted);
      padding: 3px 7px;
      background: rgba(38, 213, 228, 0.06);
      border-radius: 999px;
      border: 1px solid var(--dashboard-border);
    }
  }
}

.directory-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.sidebar-tree {
  flex: 1;
  min-height: 0;
  padding: 8px;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #071219;
}

.sidebar-tree-scroll {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  :deep(.sidebar-device-tree) {
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
      background: transparent !important;
    }

    .scroll-container {
      flex: 1;
      min-height: 0;
      background: transparent !important;
    }

    .scrollbar {
      height: 100%;
    }
  }

  // 覆盖 BasicTree 的所有背景色
  :deep(.tree) {
    background: transparent !important;
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  // 覆盖 xingyuv-tree 类的背景色
  :deep(.xingyuv-tree) {
    background: transparent !important;
  }

  // 隐藏 BasicTree 的标题栏，只保留搜索框
  :deep(.tree-header) {
    padding: 8px 0;
    border-bottom: none !important;
    background: rgba(15, 34, 73, 0.3) !important;
    margin-bottom: 8px;

    .tree-header-title {
      display: none; // 隐藏标题
    }
  }

  // 去掉搜索框下方的所有边框
  :deep(.tree-header-search) {
    border-bottom: none !important;
  }

  // 增大 xingyuv-tree-header 下方的间距
  :deep(.xingyuv-tree-header) {
    margin-bottom: 12px !important;
    border-bottom: none !important;
  }

  :deep(.ant-tree) {
    background: transparent !important;
    color: var(--dashboard-text);
  }

  /* 与分屏监控一致：叶子前占位更窄、行高更紧凑 */
  :deep(.sidebar-device-tree) {
    .ant-tree-switcher {
      width: 16px;
      margin-inline-end: 2px;
    }

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
    }

    [class*='-tree__title'] .mr-1,
    [class*='-tree__title'] .app-iconify {
      margin-right: 4px !important;
      font-size: 13px !important;
    }
  }

  // 覆盖树节点的背景
  :deep(.ant-tree-list) {
    background: transparent !important;
  }

  :deep(.ant-tree-list-holder) {
    background: transparent !important;
  }

  :deep(.ant-tree-list-holder-inner) {
    background: transparent !important;
  }

  :deep(.ant-tree-treenode) {
    background: transparent !important;
  }

  :deep(.ant-tree-node-content-wrapper) {
    background: transparent !important;
    color: rgba(200, 220, 255, 0.9);
    transition: all 0.25s;

    &:hover {
      background: rgba(56, 189, 248, 0.1) !important;
      color: var(--dashboard-text);
    }
  }

  :deep(.ant-tree-node-selected) {
    .ant-tree-node-content-wrapper {
      background: linear-gradient(90deg, rgba(56, 189, 248, 0.22), rgba(56, 189, 248, 0.1)) !important;
      color: #7dd3fc !important;
    }
  }

  :deep(.ant-tree-switcher) {
    color: var(--dashboard-blue);
    background: transparent !important;
  }

  :deep(.ant-tree-title) {
    color: inherit;
  }

  // 覆盖 Empty 组件的背景
  :deep(.ant-empty) {
    background: transparent !important;
  }

  // 搜索框样式
  :deep(.tree-header-search) {
    .ant-input {
      background: rgba(8, 22, 39, 0.8) !important;
      border: 1px solid var(--dashboard-border);
      border-radius: var(--dashboard-radius);
      color: var(--dashboard-text);

      &::placeholder {
        color: rgba(184, 203, 224, 0.48);
      }

      &:hover {
        border-color: var(--dashboard-border-strong);
        background: rgba(56, 189, 248, 0.12) !important;
      }

      &:focus {
        border-color: var(--dashboard-blue);
        box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.12);
        background: rgba(56, 189, 248, 0.14) !important;
      }
    }
  }

  /* 大屏主题：滚动条悬停可见（与分屏监控 ScrollContainer 行为一致） */
  :deep(.scrollbar__bar) {
    opacity: 0.35;
  }

  :deep(.scrollbar:hover .scrollbar__bar) {
    opacity: 1;
  }

  :deep(.scrollbar__thumb) {
    background-color: rgba(56, 189, 248, 0.45);
  }

  :deep(.scrollbar__thumb:hover) {
    background-color: rgba(56, 189, 248, 0.7);
  }
}

.sidebar-tree-scroll {
  :deep(.tree-header) {
    margin: 0 0 8px !important;
    padding: 8px 0 !important;
    background: transparent !important;
  }

  :deep(.xingyuv-tree-header) {
    margin-bottom: 8px !important;
  }

  :deep(.tree-header-search .ant-input) {
    height: 34px;
    color: var(--dashboard-text);
    background: #0a1820 !important;
    border-color: var(--dashboard-border);
    border-radius: 2px;

    &:hover,
    &:focus {
      background: #0d2029 !important;
      border-color: var(--dashboard-cyan);
      box-shadow: 0 0 0 2px rgba(38, 213, 228, 0.08);
    }
  }

  :deep(.xingyuv-tree-header .ant-input-affix-wrapper),
  :deep(.xingyuv-tree-header .ant-input) {
    color: var(--dashboard-text) !important;
    background: #0a1820 !important;
  }

  :deep(.xingyuv-tree-header .ant-input-affix-wrapper) {
    border-color: var(--dashboard-border) !important;
    border-radius: 2px 0 0 2px !important;
    box-shadow: none !important;

    &:hover,
    &:focus,
    &:focus-within {
      border-color: var(--dashboard-cyan) !important;
    }
  }

  :deep(.xingyuv-tree-header .ant-input::placeholder) {
    color: var(--dashboard-weak) !important;
  }

  :deep(.xingyuv-tree-header .ant-input-group-addon),
  :deep(.xingyuv-tree-header .ant-input-search-button) {
    color: var(--dashboard-muted) !important;
    background: #0a1820 !important;
    border-color: var(--dashboard-border) !important;
    border-radius: 0 2px 2px 0 !important;
  }

  :deep(.xingyuv-tree-header .ant-input-search-button:hover) {
    color: var(--dashboard-cyan) !important;
    border-color: var(--dashboard-cyan) !important;
  }

  :deep(.ant-tree-node-content-wrapper) {
    color: var(--dashboard-muted);
    border-radius: 1px;
    transition: color 0.18s, background 0.18s;

    &:hover {
      color: var(--dashboard-text);
      background: rgba(38, 213, 228, 0.07) !important;
    }
  }

  :deep(.ant-tree-treenode-selected .ant-tree-node-content-wrapper),
  :deep(.ant-tree-node-selected) {
    color: var(--dashboard-cyan) !important;
    background: rgba(38, 213, 228, 0.1) !important;
    box-shadow: inset 2px 0 0 var(--dashboard-cyan);
  }

  :deep(.ant-tree-switcher) {
    color: var(--dashboard-weak);
  }

  :deep(.ant-empty-description) {
    color: var(--dashboard-weak) !important;
  }

  :deep(.scrollbar__thumb) {
    background-color: var(--dashboard-border-strong);
  }
}

@media (max-width: 767px) {
  .monitor-sidebar {
    height: 360px;
    min-height: 360px;
  }

  .section-header {
    min-height: 52px;
  }
}
</style>
