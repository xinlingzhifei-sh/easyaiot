<template>
  <header class="monitor-header">
    <div class="global-bar">
      <div class="header-left">
        <div class="brand-copy">
          <div class="title-row">
            <h1 class="platform-title" data-testid="monitor-platform-title">{{ dashboardTitle }}</h1>
            <PlatformBrandingFab />
          </div>
          <div class="platform-subtitle">COMMAND CENTER · ONLINE</div>
        </div>
      </div>

      <div class="command-scope">
        <span class="scope-label">当前监控域</span>
        <strong class="scope-value">全域设备 / 实时监控</strong>
      </div>

      <div class="header-right">
        <div
          :class="['health-status', `health-status--${safeDashboardHealth.status}`]"
          :title="safeDashboardHealth.detail"
        >
          <span class="health-dot" aria-hidden="true"></span>
          <span>{{ safeDashboardHealth.label }}</span>
        </div>
        <div class="refresh-info" :title="safeDashboardHealth.detail">
          数据更新 {{ safeLastUpdatedText }}
        </div>
        <div class="date-time">
          <strong class="current-clock">{{ currentClock }}</strong>
          <span>{{ currentDate }} · {{ currentDay }}</span>
        </div>
        <button
          type="button"
          class="user-role"
          data-testid="monitor-admin-entry"
          :aria-label="adminEntryLabel"
          @click="handleGoToAdmin"
        >
          {{ adminEntryLabel }}
        </button>
      </div>
    </div>
    <div class="kpi-rail" aria-label="实时运行指标">
      <div
        v-for="metric in kpiMetrics"
        :key="metric.label"
        :class="['kpi-item', metric.tone ? `kpi-item--${metric.tone}` : '']"
      >
        <span class="kpi-index">{{ metric.index }}</span>
        <div class="kpi-copy">
          <span class="kpi-label">{{ metric.label }}</span>
          <span class="kpi-hint">{{ metric.hint }}</span>
        </div>
        <strong class="kpi-value">{{ metric.value }}</strong>
        <span class="kpi-unit">{{ metric.unit }}</span>
      </div>
    </div>
  </header>
</template>

<script lang="ts" setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { resolveAdminEntryTarget } from '../adminEntry'
import { usePlatformBranding } from '@/hooks/web/usePlatformBranding'
import PlatformBrandingFab from './PlatformBrandingFab.vue'
import type { DashboardHealth, DashboardStatistics } from '../useDashboardData'

defineOptions({
  name: 'MonitorHeader'
})

const props = withDefaults(defineProps<{
  activeVideos?: any[]
  statistics?: DashboardStatistics
  todayAlarmCount?: number
  dashboardHealth?: DashboardHealth
  lastUpdatedText?: string
}>(), {
  activeVideos: () => [],
  todayAlarmCount: 0,
})

const emit = defineEmits<{
  (e: 'admin-entry'): void
}>()

const router = useRouter()
const adminEntryLabel = '管理后台'
const { config } = usePlatformBranding()
const dashboardTitle = computed(() => config.value.dashboardTitle)
const safeDashboardHealth = computed<DashboardHealth>(() => props.dashboardHealth ?? {
  status: 'loading',
  label: '检查中',
  detail: '等待接口刷新',
  lastUpdatedAt: null,
})
const safeLastUpdatedText = computed(() => props.lastUpdatedText || '--')
const kpiMetrics = computed(() => [
  {
    index: '01',
    label: '接入设备',
    hint: 'DEVICE ACCESS',
    value: props.statistics?.cameraCount ?? 0,
    unit: '台',
  },
  {
    index: '02',
    label: '实时画面',
    hint: 'LIVE FEEDS',
    value: props.activeVideos.length,
    unit: '路',
    tone: 'running',
  },
  {
    index: '03',
    label: '启用算法',
    hint: 'ALGORITHMS',
    value: props.statistics?.algorithmCount ?? 0,
    unit: '项',
  },
  {
    index: '04',
    label: '今日告警',
    hint: 'INCIDENTS',
    value: props.todayAlarmCount,
    unit: '次',
    tone: 'attention',
  },
  {
    index: '05',
    label: '模型资源',
    hint: 'MODEL ASSETS',
    value: props.statistics?.modelCount ?? 0,
    unit: '个',
  },
])

const handleGoToAdmin = () => {
  emit('admin-entry')
  router.push(resolveAdminEntryTarget(router))
}

const currentDate = ref('')
const currentDay = ref('')
const currentClock = ref('')

const updateDateTime = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentDate.value = `${year}.${month}.${day}`
  currentClock.value = `${hours}:${minutes}:${seconds}`

  const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  currentDay.value = weekDays[now.getDay()]
}

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  updateDateTime()
  timer = setInterval(updateDateTime, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="less" scoped>
.monitor-header {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  background: #07141d;
  border-bottom: 1px solid var(--dashboard-border);
}

.global-bar {
  height: 72px;
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(220px, 0.72fr) minmax(430px, 1.25fr);
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  border-bottom: 1px solid rgba(29, 53, 65, 0.72);
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.title-row {
  min-width: 0;
  display: flex;
  align-items: center;
}

.platform-title {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--dashboard-text);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.platform-subtitle {
  color: var(--dashboard-weak);
  font-family: 'IBM Plex Mono', Consolas, monospace;
  font-size: 10px;
  letter-spacing: 0.12em;
}

.command-scope {
  min-width: 0;
  padding-left: 18px;
  border-left: 2px solid var(--dashboard-cyan);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.scope-label {
  color: var(--dashboard-weak);
  font-size: 11px;
}

.scope-value {
  overflow: hidden;
  color: var(--dashboard-text);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-right {
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
}

.health-status {
  height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  color: var(--dashboard-muted);
  background: rgba(14, 32, 43, 0.82);
  border: 1px solid var(--dashboard-border);
  border-radius: 999px;
  font-size: 12px;
  white-space: nowrap;
}

.health-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--dashboard-muted);
}

.health-status--online {
  color: var(--dashboard-green);

  .health-dot {
    background: var(--dashboard-green);
    box-shadow: 0 0 0 3px rgba(61, 220, 151, 0.12);
  }
}

.health-status--degraded {
  color: var(--dashboard-amber);

  .health-dot {
    background: var(--dashboard-amber);
  }
}

.health-status--offline {
  color: var(--dashboard-red);

  .health-dot {
    background: var(--dashboard-red);
  }
}

.refresh-info {
  max-width: 132px;
  overflow: hidden;
  color: var(--dashboard-weak);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.date-time {
  min-width: 104px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-family: 'IBM Plex Mono', Consolas, monospace;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  .current-clock {
    color: var(--dashboard-text);
    font-size: 17px;
    font-weight: 600;
    line-height: 1;
  }

  span {
    color: var(--dashboard-weak);
    font-size: 9px;
  }
}

.user-role {
  height: 32px;
  appearance: none;
  padding: 0 13px;
  color: var(--dashboard-text);
  background: transparent;
  border: 1px solid var(--dashboard-border-strong);
  border-radius: 2px;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.18s, border-color 0.18s, background 0.18s;

  &:hover,
  &:focus-visible {
    color: var(--dashboard-cyan);
    background: rgba(38, 213, 228, 0.08);
    border-color: var(--dashboard-cyan);
    outline: none;
  }
}

.kpi-rail {
  min-height: 64px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 0 16px;
  background: #081820;
}

.kpi-item {
  min-width: 0;
  display: grid;
  grid-template-columns: 24px minmax(76px, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-right: 1px solid var(--dashboard-border);

  &:first-child {
    border-left: 1px solid var(--dashboard-border);
  }
}

.kpi-index {
  color: var(--dashboard-weak);
  font-family: 'IBM Plex Mono', Consolas, monospace;
  font-size: 10px;
}

.kpi-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kpi-label {
  overflow: hidden;
  color: var(--dashboard-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-hint {
  overflow: hidden;
  color: var(--dashboard-weak);
  font-family: 'IBM Plex Mono', Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.08em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-value {
  color: var(--dashboard-text);
  font-family: 'IBM Plex Mono', Consolas, monospace;
  font-size: clamp(21px, 1.55vw, 28px);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.kpi-unit {
  align-self: end;
  padding-bottom: 3px;
  color: var(--dashboard-weak);
  font-size: 10px;
}

.kpi-item--running .kpi-value {
  color: var(--dashboard-cyan);
}

.kpi-item--attention .kpi-value {
  color: var(--dashboard-amber);
}

@media (max-width: 1440px) {
  .global-bar {
    height: 64px;
    grid-template-columns: minmax(236px, 0.9fr) minmax(190px, 0.65fr) minmax(350px, 1.2fr);
    gap: 16px;
    padding: 0 16px;
  }

  .platform-title {
    font-size: 19px;
  }

  .header-right {
    gap: 10px;
  }

  .refresh-info {
    display: none;
  }

  .kpi-rail {
    min-height: 58px;
  }

  .kpi-item {
    grid-template-columns: 18px minmax(64px, 1fr) auto auto;
    gap: 7px;
    padding: 8px 12px;
  }
}

@media (max-width: 1180px) {
  .global-bar {
    grid-template-columns: minmax(220px, 1fr) minmax(340px, 1.2fr);
  }

  .command-scope {
    display: none;
  }

  .kpi-index,
  .kpi-hint {
    display: none;
  }

  .kpi-item {
    grid-template-columns: minmax(58px, 1fr) auto auto;
  }
}

@media (max-width: 767px) {
  .global-bar {
    height: 62px;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px;
    padding: 0 10px;
  }

  .platform-title {
    max-width: 150px;
    font-size: 16px;
  }

  .platform-subtitle,
  .refresh-info,
  .health-status span:last-child,
  .date-time span {
    display: none;
  }

  .header-right {
    gap: 7px;
  }

  .health-status {
    width: 28px;
    padding: 0;
    justify-content: center;
    border-radius: 50%;
  }

  .date-time {
    min-width: auto;

    .current-clock {
      font-size: 13px;
    }
  }

  .user-role {
    width: 32px;
    padding: 0;
    overflow: hidden;
    color: transparent;
    font-size: 0;

    &::before {
      content: '管';
      color: var(--dashboard-text);
      font-size: 12px;
    }
  }

  .kpi-rail {
    min-height: 58px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding: 0 8px;
  }

  .kpi-item {
    grid-template-columns: minmax(0, 1fr) auto auto;
    gap: 4px;
    padding: 8px;
  }

  .kpi-item:nth-child(3),
  .kpi-item:nth-child(5) {
    display: none;
  }

  .kpi-label {
    font-size: 10px;
  }

  .kpi-value {
    font-size: 18px;
  }

  .kpi-unit {
    font-size: 9px;
  }
}
</style>
