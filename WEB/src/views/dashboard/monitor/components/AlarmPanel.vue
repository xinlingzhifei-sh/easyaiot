<template>
  <div class="alarm-panel">
    <div class="panel-header">
      <div class="header-copy">
        <span class="header-kicker">INCIDENT QUEUE</span>
        <div class="header-title">告警处置队列</div>
      </div>
      <div class="header-count">
        <span>今日</span>
        <strong class="count-number">{{ todayAlarmCount }}</strong>
        <span>次</span>
      </div>
    </div>
    
    <div class="panel-content">
      <div
        v-if="dashboardHealth.status === 'offline' || dashboardHealth.status === 'degraded'"
        class="panel-error-state"
        role="alert"
      >
        <Icon icon="ant-design:disconnect-outlined" :size="22" />
        <div class="panel-error-copy">
          <strong>告警接口异常</strong>
          <span>{{ dashboardHealth.detail }}</span>
        </div>
        <button type="button" class="panel-retry" :disabled="refreshing" @click="handleRetry">
          {{ refreshing ? '重试中' : '重新加载' }}
        </button>
      </div>

      <div
        v-for="alarm in alarmList"
        :key="alarm.id"
        :class="['alarm-item', `alarm-item--${getDispositionStatus(alarm)}`]"
        @click="handleAlarmClick(alarm)"
      >
        <div class="alarm-image">
          <img 
            v-if="getImageUrl(alarm) && !alarm.imageError" 
            :src="getImageUrl(alarm)" 
            alt="告警图片"
            class="alarm-img"
            @error="handleImageError(alarm)"
            @load="handleImageLoad(alarm)"
          />
          <div v-else class="alarm-icon">
            <Icon 
              :icon="getAlarmIcon(alarm.type)" 
              :size="32"
              :color="getAlarmColor(alarm.level)"
            />
          </div>
        </div>
        
        <div class="alarm-info">
          <div class="alarm-title-row">
            <span class="alarm-status-dot" aria-hidden="true"></span>
            <div class="alarm-title">{{ alarm.title || alarm.event || '未知事件' }}</div>
          </div>
          <div class="alarm-meta">
            <span 
              :class="['task-type-tag', getTaskTypeClass(alarm)]"
            >
              {{ getTaskTypeText(alarm) }}
            </span>
            <span :class="['disposition-tag', getDispositionStatusClass(alarm)]">
              {{ getDispositionStatusText(alarm) }}
            </span>
            <span class="alarm-location">{{ alarm.device_name || alarm.location || '未知设备' }}</span>
          </div>
          <div class="alarm-footer">
            <span class="alarm-time">{{ alarm.time }}</span>
            <span class="alarm-action">
              查看画面
              <Icon icon="ant-design:right-outlined" :size="10" />
            </span>
          </div>
        </div>
      </div>
      
      <div
        v-if="alarmList.length === 0 && dashboardHealth.status !== 'offline' && dashboardHealth.status !== 'degraded'"
        class="empty-state"
      >
        <Icon icon="ant-design:inbox-outlined" :size="48" />
        <div class="empty-text">暂无告警信息</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { Icon } from '@/components/Icon'
import { resolveAlertImageDisplayUrl } from '@/utils/alertMinioImage'
import type { DashboardHealth } from '../useDashboardData'

defineOptions({
  name: 'AlarmPanel'
})

withDefaults(defineProps<{
  alarmList?: any[]
  todayAlarmCount?: number
  dashboardHealth?: DashboardHealth
  refreshing?: boolean
}>(), {
  alarmList: () => [],
  todayAlarmCount: 0,
  dashboardHealth: () => ({
    status: 'loading',
    label: '检查中',
    detail: '等待接口刷新',
    lastUpdatedAt: null,
  }),
  refreshing: false,
})

const emit = defineEmits<{
  'play-alarm': [alarm: any]
  retry: []
}>()

const handleRetry = () => emit('retry')

const handleAlarmClick = (alarm: any) => {
  emit('play-alarm', alarm)
}

// 获取告警图标
const getAlarmIcon = (type: string) => {
  const iconMap: Record<string, string> = {
    fire: 'ant-design:fire-outlined',
    smoke: 'ant-design:cloud-outlined',
    intrusion: 'ant-design:warning-outlined',
    default: 'ant-design:exclamation-circle-outlined'
  }
  return iconMap[type] || iconMap.default
}

// 获取告警颜色
const getAlarmColor = (level: string) => {
  const colorMap: Record<string, string> = {
    '一级': '#ff4d4f',
    '二级': '#ff9800',
    '三级': '#ffc107',
    '四级': '#1890ff'
  }
  return colorMap[level] || '#ff4d4f'
}

// 获取任务类型
const getTaskType = (alarm: any): string | null => {
  // 优先从 information 字段中获取 task_type
  let taskType = null
  if (alarm.information) {
    if (typeof alarm.information === 'object' && alarm.information.task_type) {
      taskType = alarm.information.task_type
    } else if (typeof alarm.information === 'string') {
      try {
        const info = JSON.parse(alarm.information)
        taskType = info?.task_type
      } catch (e) {
        // 解析失败，忽略
      }
    }
  }
  
  // 如果 information 中没有，尝试从 alarm 本身获取
  if (!taskType && alarm.task_type) {
    taskType = alarm.task_type
  }
  
  return taskType
}

// 获取任务类型文本
const getTaskTypeText = (alarm: any): string => {
  const taskType = getTaskType(alarm)
  
  // 根据 task_type 返回文本
  if (taskType === 'snap' || taskType === 'snapshot') {
    return '抓拍'
  } else {
    return '实时'
  }
}

// 获取任务类型样式类
const getTaskTypeClass = (alarm: any): string => {
  const taskType = getTaskType(alarm)
  
  // 根据 task_type 返回样式类
  if (taskType === 'snap' || taskType === 'snapshot') {
    return 'task-type-snap'
  } else {
    return 'task-type-realtime'
  }
}

type DispositionStatus = 'unconfirmed' | 'confirmed' | 'processed' | 'false-positive'

const normalizeStatusValue = (value: unknown) => String(value ?? '').trim().toLowerCase()

const getDispositionStatus = (alarm: any): DispositionStatus => {
  const rawStatus = [
    alarm?.disposition_status,
    alarm?.review_status,
    alarm?.handle_status,
    alarm?.status,
    alarm?.state,
  ].map(normalizeStatusValue).find(Boolean) || ''

  if (
    alarm?.is_false_positive === true ||
    alarm?.false_positive === true ||
    rawStatus.includes('false') ||
    rawStatus.includes('误报')
  ) {
    return 'false-positive'
  }

  if (
    alarm?.processed_at ||
    alarm?.handled_at ||
    alarm?.closed_at ||
    ['processed', 'handled', 'closed', 'resolved', 'done', '已处理'].includes(rawStatus)
  ) {
    return 'processed'
  }

  if (
    alarm?.confirmed_at ||
    alarm?.ack_at ||
    alarm?.acknowledged_at ||
    ['confirmed', 'ack', 'acknowledged', 'reviewed', '已确认'].includes(rawStatus)
  ) {
    return 'confirmed'
  }

  return 'unconfirmed'
}

const getDispositionStatusText = (alarm: any): string => {
  const textMap: Record<DispositionStatus, string> = {
    unconfirmed: '未确认',
    confirmed: '已确认',
    processed: '已处理',
    'false-positive': '误报',
  }
  return textMap[getDispositionStatus(alarm)]
}

const getDispositionStatusClass = (alarm: any): string => {
  return `disposition-${getDispositionStatus(alarm)}`
}

// 获取图片展示 URL（与告警列表页一致，兼容 mini 本地路径 /video/alert/image）
const getImageUrl = (alarm: any): string | undefined => {
  if (alarm.image) return alarm.image
  const raw = alarm.image_url
  if (!raw) return undefined
  const resolved = resolveAlertImageDisplayUrl(raw)
  return resolved || undefined
}

// 处理图片加载错误
const handleImageError = (alarm: any) => {
  // 标记图片加载失败，显示占位图标
  alarm.imageError = true
}

// 处理图片加载成功
const handleImageLoad = (alarm: any) => {
  // 确保清除错误标记
  alarm.imageError = false
}
</script>

<style lang="less" scoped>
.alarm-panel {
  width: 100%;
  height: 100%;
  padding: 0;
  background: var(--dashboard-panel);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  border: 1px solid var(--dashboard-border);
  box-shadow: none;
  border-radius: var(--dashboard-radius);
  padding: 0;

  &::before {
    display: none;
  }
}

.panel-header {
  text-align: left;
  background: var(--dashboard-panel-strong);
  border-bottom: 1px solid var(--dashboard-border);
  color: var(--dashboard-text);
  font-size: 16px;
  min-height: 58px;
  line-height: 1.2;
  letter-spacing: 0;
  padding: 10px 12px;
  display: flex;
  flex-direction: row;
  gap: 4px;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;

  .header-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .header-kicker {
    color: var(--dashboard-weak);
    font-family: 'IBM Plex Mono', Consolas, monospace;
    font-size: 8px;
    letter-spacing: 0.1em;
  }

  .header-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--dashboard-text);
    line-height: 1.2;
  }

  .header-count {
    height: 28px;
    padding: 0 8px;
    display: flex;
    align-items: baseline;
    gap: 4px;
    font-size: 10px;
    color: var(--dashboard-muted);
    line-height: 1.2;
    background: rgba(245, 185, 66, 0.08);
    border: 1px solid rgba(245, 185, 66, 0.28);
    border-radius: 999px;

    .count-number {
      color: var(--dashboard-amber);
      font-weight: 700;
      font-size: 18px;
      font-variant-numeric: tabular-nums;
    }
  }
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.04);
    border-radius: 0;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--dashboard-border-strong);
    border-radius: 0;

    &:hover {
      background: var(--dashboard-cyan);
    }
  }
}

.alarm-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  margin-bottom: 0;
  background: #091820;
  border: 0;
  border-bottom: 1px solid var(--dashboard-border);
  border-left: 2px solid var(--dashboard-amber);
  border-radius: 0;
  transition: background 0.18s, border-color 0.18s;
  position: relative;
  cursor: pointer;

  &:hover {
    background: #0d222c;
    border-left-color: var(--dashboard-cyan);
  }

  &:last-child {
    border-bottom: 0;
  }
}

.alarm-item--confirmed {
  border-left-color: var(--dashboard-cyan);
}

.alarm-item--processed {
  border-left-color: var(--dashboard-green);
}

.alarm-item--false-positive {
  border-left-color: var(--dashboard-weak);
}

.alarm-image {
  width: 72px;
  height: 54px;
  flex-shrink: 0;
  border-radius: 1px;
  overflow: hidden;
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;

  .alarm-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    cursor: pointer;
  }

  .alarm-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.alarm-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.alarm-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
}

.alarm-status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: var(--dashboard-amber);
  border-radius: 50%;
}

.alarm-item--confirmed .alarm-status-dot {
  background: var(--dashboard-cyan);
}

.alarm-item--processed .alarm-status-dot {
  background: var(--dashboard-green);
}

.alarm-item--false-positive .alarm-status-dot {
  background: var(--dashboard-weak);
}

.alarm-title {
  min-width: 0;
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--dashboard-text);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alarm-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.task-type-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 10px;
  border-radius: var(--dashboard-radius);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  transition: background 0.2s, border-color 0.2s, transform 0.2s;

  &.task-type-realtime {
    background: rgba(56, 189, 248, 0.14);
    color: #bdefff;
    border: 1px solid rgba(56, 189, 248, 0.34);

    &:hover {
      transform: translateY(-1px);
      background: rgba(56, 189, 248, 0.22);
    }
  }

  &.task-type-snap {
    background: rgba(34, 197, 94, 0.14);
    color: #bbf7d0;
    border: 1px solid rgba(34, 197, 94, 0.32);

    &:hover {
      transform: translateY(-1px);
      background: rgba(34, 197, 94, 0.22);
    }
  }
}

.disposition-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: 58px;
  padding: 3px 8px;
  border-radius: var(--dashboard-radius);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border: 1px solid rgba(184, 203, 224, 0.22);
  background: rgba(184, 203, 224, 0.1);
  color: rgba(230, 241, 255, 0.72);

  &.disposition-confirmed {
    color: #bdefff;
    border-color: rgba(56, 189, 248, 0.34);
    background: rgba(56, 189, 248, 0.14);
  }

  &.disposition-processed {
    color: #bbf7d0;
    border-color: rgba(34, 197, 94, 0.32);
    background: rgba(34, 197, 94, 0.14);
  }

  &.disposition-false-positive {
    color: #fed7aa;
    border-color: rgba(245, 158, 11, 0.36);
    background: rgba(245, 158, 11, 0.14);
  }
}

.alarm-location {
  font-size: 12px;
  color: var(--dashboard-muted);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alarm-time {
  font-size: 12px;
  color: rgba(184, 203, 224, 0.56);
  font-variant-numeric: tabular-nums;
}

.alarm-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.alarm-action {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--dashboard-cyan);
  font-size: 10px;
  white-space: nowrap;
}

.panel-error-state {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  margin-bottom: 12px;
  padding: 12px;
  color: #fecaca;
  background: rgba(127, 29, 29, 0.22);
  border: 1px solid rgba(248, 113, 113, 0.34);
  border-radius: var(--dashboard-radius);
}

.panel-error-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;

  strong {
    color: #fee2e2;
    font-size: 13px;
  }

  span {
    color: rgba(254, 226, 226, 0.72);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

.panel-retry {
  grid-column: 2;
  width: fit-content;
  padding: 5px 10px;
  color: var(--dashboard-text);
  background: transparent;
  border: 1px solid var(--dashboard-border-strong);
  border-radius: var(--dashboard-radius);
  cursor: pointer;

  &:hover:not(:disabled) {
    border-color: var(--dashboard-amber);
    color: var(--dashboard-amber);
  }

  &:disabled {
    cursor: wait;
    opacity: 0.55;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(184, 203, 224, 0.52);

  .empty-text {
    margin-top: 16px;
    font-size: 14px;
  }
}

@media (max-width: 1180px) {
  .alarm-item {
    padding: 10px;
  }

  .alarm-image {
    width: 60px;
    height: 48px;
  }
}

@media (max-width: 767px) {
  .alarm-panel {
    height: 340px;
    min-height: 340px;
  }

  .panel-header {
    min-height: 52px;
  }
}

</style>
