import { computed, onMounted, onUnmounted, ref, watch, type ComputedRef } from 'vue'
import { getDashboardStatistics, queryAlarmList } from '@/api/device/calculate'
import { resolveAlertImageDisplayUrl } from '@/utils/alertMinioImage'
import { formatAlertListTitle } from '@/views/alert/alertDisplay'

const DASHBOARD_REFRESH_INTERVAL_MS = 5000
const DASHBOARD_ALARM_PAGE_SIZE = 7

export type DashboardHealthStatus = 'loading' | 'online' | 'degraded' | 'offline'

export type DashboardHealth = {
  status: DashboardHealthStatus
  label: string
  detail: string
  lastUpdatedAt: number | null
}

export type DashboardStatistics = {
  alarmCount: number
  todayAlarmCount: number
  cameraCount: number
  algorithmCount: number
  modelCount: number
}

const EMPTY_STATISTICS: DashboardStatistics = {
  alarmCount: 0,
  todayAlarmCount: 0,
  cameraCount: 0,
  algorithmCount: 0,
  modelCount: 0,
}

function toNumber(value: unknown) {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function mapDashboardStatistics(raw: any): DashboardStatistics {
  return {
    alarmCount: toNumber(raw?.alarm_count ?? raw?.alarmCount),
    todayAlarmCount: toNumber(raw?.today_alarm_count ?? raw?.todayAlarmCount),
    cameraCount: toNumber(raw?.camera_count ?? raw?.cameraCount),
    algorithmCount: toNumber(raw?.algorithm_count ?? raw?.algorithmCount),
    modelCount: toNumber(raw?.model_count ?? raw?.modelCount),
  }
}

function resolveAlarmLevel(item: any) {
  if (item?.level) return item.level
  const event = String(item?.event ?? '')
  if (event.includes('火') || event.includes('fire')) return '一级'
  if (event.includes('烟') || event.includes('smoke')) return '二级'
  return '三级'
}

function resolveAlarmType(item: any) {
  const event = String(item?.event ?? '')
  if (event.includes('火') || event.includes('fire')) return 'fire'
  if (event.includes('烟') || event.includes('smoke')) return 'smoke'
  if (event.includes('入侵') || event.includes('intrusion')) return 'intrusion'
  return 'default'
}

function mapDashboardAlarm(item: any) {
  const imageUrl = resolveAlertImageDisplayUrl(item?.image_url) || null
  return {
    ...item,
    id: item?.id || item?.alert_id,
    type: resolveAlarmType(item),
    title: formatAlertListTitle(item),
    event: item?.event,
    level: resolveAlarmLevel(item),
    location: item?.device_name || item?.location || '未知设备',
    time: item?.time || item?.alert_time || item?.created_at || '',
    image: imageUrl,
    image_url: item?.image_url,
    device_name: item?.device_name,
    device_id: item?.device_id,
    task_type: item?.task_type,
    information: item?.information,
    matched_person_name: item?.matched_person_name,
    source_event: item?.source_event,
  }
}

function getErrorMessage(error: unknown) {
  const status = (error as any)?.response?.status ?? (error as any)?.response?.data?.code
  const message = error instanceof Error
    ? error.message
    : typeof error === 'string'
      ? error
      : ''
  if (status === 403 || /\b403\b|forbidden|无权|没有权限/i.test(message)) {
    return '当前账号无权读取该设备告警'
  }
  if (message.trim()) return message
  return '接口刷新失败'
}

function formatLastUpdatedAt(timestamp: number | null) {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

export function useDashboardData(activeDeviceId: ComputedRef<string>) {
  const statistics = ref<DashboardStatistics>({ ...EMPTY_STATISTICS })
  const alarmList = ref<any[]>([])
  const refreshing = ref(false)
  const lastUpdatedAt = ref<number | null>(null)
  const lastErrorAt = ref<number | null>(null)
  const lastErrorMessage = ref('')
  const lastRefreshHadError = ref(false)
  let refreshTimer: ReturnType<typeof setInterval> | null = null
  let disposed = false

  const dashboardHealth = computed<DashboardHealth>(() => {
    if (!activeDeviceId.value.trim()) {
      return {
        status: 'loading',
        label: '待选择',
        detail: '请选择监控设备',
        lastUpdatedAt: null,
      }
    }

    if (!lastUpdatedAt.value && refreshing.value) {
      return {
        status: 'loading',
        label: '检查中',
        detail: '正在刷新接口',
        lastUpdatedAt: null,
      }
    }

    if (!lastUpdatedAt.value && lastErrorAt.value) {
      return {
        status: 'offline',
        label: '离线',
        detail: lastErrorMessage.value || '接口未成功',
        lastUpdatedAt: null,
      }
    }

    if (lastUpdatedAt.value && lastRefreshHadError.value && (lastErrorAt.value ?? 0) >= lastUpdatedAt.value) {
      return {
        status: 'degraded',
        label: '异常',
        detail: lastErrorMessage.value || '部分数据刷新失败',
        lastUpdatedAt: lastUpdatedAt.value,
      }
    }

    return {
      status: lastUpdatedAt.value ? 'online' : 'loading',
      label: lastUpdatedAt.value ? '在线' : '检查中',
      detail: lastUpdatedAt.value ? '最近一次接口刷新成功' : '等待接口刷新',
      lastUpdatedAt: lastUpdatedAt.value,
    }
  })

  const todayAlarmCount = computed(() => statistics.value.todayAlarmCount)
  const lastUpdatedText = computed(() => formatLastUpdatedAt(lastUpdatedAt.value))

  async function refreshDashboardData() {
    const deviceId = activeDeviceId.value.trim()
    if (!deviceId || refreshing.value) return
    refreshing.value = true
    const [statisticsResult, alarmResult] = await Promise.allSettled([
      getDashboardStatistics({ device_id: deviceId }),
      queryAlarmList({
        pageNo: 1,
        pageSize: DASHBOARD_ALARM_PAGE_SIZE,
        device_id: deviceId,
      }, { polling: true }),
    ])

    if (disposed || deviceId !== activeDeviceId.value.trim()) {
      refreshing.value = false
      return
    }

    const now = Date.now()
    const errors: string[] = []
    let hadSuccess = false

    if (statisticsResult.status === 'fulfilled') {
      statistics.value = mapDashboardStatistics(statisticsResult.value)
      hadSuccess = true
    } else {
      errors.push(getErrorMessage(statisticsResult.reason))
    }

    if (alarmResult.status === 'fulfilled') {
      const rawList = Array.isArray(alarmResult.value?.alert_list) ? alarmResult.value.alert_list : []
      alarmList.value = rawList.map(mapDashboardAlarm)
      hadSuccess = true
    } else {
      errors.push(getErrorMessage(alarmResult.reason))
    }

    if (hadSuccess) {
      lastUpdatedAt.value = now
    }

    lastRefreshHadError.value = errors.length > 0
    if (errors.length) {
      lastErrorAt.value = now
      lastErrorMessage.value = errors.join('；')
      console.error('刷新首页大屏数据失败', errors)
    } else {
      lastErrorMessage.value = ''
    }

    refreshing.value = false
  }

  onMounted(() => {
    disposed = false
    refreshDashboardData()
    refreshTimer = setInterval(refreshDashboardData, DASHBOARD_REFRESH_INTERVAL_MS)
  })

  watch(activeDeviceId, () => {
    statistics.value = { ...EMPTY_STATISTICS }
    alarmList.value = []
    lastUpdatedAt.value = null
    lastErrorAt.value = null
    lastErrorMessage.value = ''
    lastRefreshHadError.value = false
    refreshDashboardData()
  }, { flush: 'post' })

  onUnmounted(() => {
    disposed = true
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  })

  return {
    alarmList,
    dashboardHealth,
    lastUpdatedAt,
    lastUpdatedText,
    refreshDashboardData,
    refreshing,
    statistics,
    todayAlarmCount,
  }
}
