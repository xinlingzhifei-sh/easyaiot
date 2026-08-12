export const DASHBOARD_GUARD_TASK_PREFIX = '[Dashboard Guard]'

export interface DashboardGuardScope {
  key: string
  label: string
  deviceIds: string[]
}

export interface DashboardGuardTask {
  id: number
  task_name?: string
  task_type?: string
  device_ids?: string[] | string
  model_ids?: number[] | string
  extract_interval?: number
  tracking_enabled?: boolean
  tracking_similarity_threshold?: number
  tracking_max_age?: number
  tracking_smooth_alpha?: number
  alert_event_enabled?: boolean | number
  alert_event_suppress_time?: number
  alert_class_names?: string[] | string
  face_detection_enabled?: boolean
  plate_detection_enabled?: boolean
  alert_notification_enabled?: boolean
  alert_notification_config?: unknown
  alarm_suppress_time?: number
  defense_mode?: string
  defense_schedule?: string | number[][]
  schedule_policy?: 'local' | 'auto' | 'node'
  target_node_id?: number | null
  is_enabled?: boolean | number
  run_status?: string
}

export interface DashboardGuardTaskPayload {
  task_name: string
  task_type: 'realtime'
  device_ids: string[]
  model_ids: number[]
  extract_interval?: number
  tracking_enabled?: boolean
  tracking_similarity_threshold?: number
  tracking_max_age?: number
  tracking_smooth_alpha?: number
  alert_event_enabled: true
  alert_event_suppress_time?: number
  alert_class_names: string[]
  face_detection_enabled: true
  plate_detection_enabled: true
  alert_notification_enabled?: boolean
  alert_notification_config?: unknown
  alarm_suppress_time?: number
  is_enabled: boolean
  defense_mode?: string
  defense_schedule?: string | number[][]
  schedule_policy?: 'local' | 'auto' | 'node'
  target_node_id?: number | null
}

export interface DashboardGuardTaskApi {
  listAlgorithmTasks: (params?: Record<string, unknown>) => Promise<unknown>
  listAvailableModels: () => Promise<unknown>
  createAlgorithmTask: (payload: DashboardGuardTaskPayload) => Promise<unknown>
  startAlgorithmTask: (taskId: number) => Promise<unknown>
  stopAlgorithmTask: (taskId: number) => Promise<unknown>
}

interface DashboardGuardNode {
  key?: string | number
  title?: unknown
  device?: { id?: string | number; name?: string }
  children?: DashboardGuardNode[]
}

interface StartDashboardGuardTaskOptions {
  scope: DashboardGuardScope
  api: DashboardGuardTaskApi
}

interface GuardStateOptions {
  scope: DashboardGuardScope | null
  api: DashboardGuardTaskApi
}

function normalizeTaskList(response: unknown): DashboardGuardTask[] {
  if (Array.isArray(response)) return response as DashboardGuardTask[]
  if (response && typeof response === 'object') {
    const data = (response as { data?: unknown }).data
    if (Array.isArray(data)) return data as DashboardGuardTask[]
    if (data && typeof data === 'object' && Array.isArray((data as { data?: unknown }).data)) {
      return (data as { data: DashboardGuardTask[] }).data
    }
  }
  return []
}

async function listRealtimeTasks(api: DashboardGuardTaskApi): Promise<DashboardGuardTask[]> {
  const response = await api.listAlgorithmTasks({
    pageNo: 1,
    pageSize: 1000,
    task_type: 'realtime',
  })
  return normalizeTaskList(response).filter((task) => task.task_type === 'realtime' || !task.task_type)
}

function normalizeNumberIds(value: DashboardGuardTask['model_ids']): number[] {
  if (Array.isArray(value)) return value.map(Number).filter((id) => Number.isInteger(id) && id > 0)
  if (typeof value !== 'string') return []
  const trimmed = value.trim()
  if (!trimmed) return []
  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) {
      return parsed.map(Number).filter((id) => Number.isInteger(id) && id > 0)
    }
  } catch {
    // Some legacy responses use comma-separated ids instead of JSON.
  }
  return trimmed
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isInteger(id) && id > 0)
}

function normalizeAvailableModelIds(response: unknown): number[] {
  const record = response && typeof response === 'object'
    ? response as Record<string, unknown>
    : null
  const data = record?.data && typeof record.data === 'object'
    ? record.data as Record<string, unknown>
    : null
  const candidates = [
    response,
    record?.data,
    record?.list,
    record?.records,
    data?.data,
    data?.list,
    data?.records,
  ]
  const models = candidates.find(Array.isArray) as unknown[] | undefined
  if (!models) return []
  return [...new Set(models
    .map((model) => model && typeof model === 'object' ? (model as { id?: unknown }).id : model)
    .map(Number)
    .filter((id) => Number.isInteger(id) && id > 0))]
}

function normalizeStringIds(value: DashboardGuardTask['device_ids']): string[] {
  if (Array.isArray(value)) return value.map(String).filter(Boolean)
  if (typeof value !== 'string') return []
  const trimmed = value.trim()
  if (!trimmed) return []
  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
  } catch {
    // Some legacy responses use comma-separated ids instead of JSON.
  }
  return trimmed
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeAlertClassNames(value: DashboardGuardTask['alert_class_names']): string[] {
  return normalizeStringIds(value)
}

function uniqueStrings(values: string[]): string[] {
  return [...new Set(values.map(String).filter(Boolean))]
}

function sameDeviceSet(left: string[], right: string[]) {
  const a = uniqueStrings(left).sort()
  const b = uniqueStrings(right).sort()
  return a.length === b.length && a.every((value, index) => value === b[index])
}

function isTaskEnabled(task: DashboardGuardTask) {
  if (typeof task.run_status === 'string' && task.run_status.length > 0) {
    return task.run_status === 'running'
  }
  return task.is_enabled === true || task.is_enabled === 1
}

function isAlertEventEnabled(task: DashboardGuardTask) {
  return task.alert_event_enabled === true || task.alert_event_enabled === 1
}

export function buildDashboardGuardTaskName(scope: DashboardGuardScope) {
  return `${DASHBOARD_GUARD_TASK_PREFIX} ${scope.label} (${scope.key})`
}

export function isDashboardGuardTask(task: DashboardGuardTask) {
  return String(task.task_name || '').startsWith(DASHBOARD_GUARD_TASK_PREFIX)
}

export function isDashboardGuardTaskForScope(task: DashboardGuardTask, scope: DashboardGuardScope) {
  const taskName = String(task.task_name || '')
  return isDashboardGuardTask(task) && taskName.endsWith(`(${scope.key})`)
}

function findReusableDashboardTask(tasks: DashboardGuardTask[], scope: DashboardGuardScope) {
  return tasks.find(
    (task) =>
      isDashboardGuardTaskForScope(task, scope) &&
      sameDeviceSet(normalizeStringIds(task.device_ids), scope.deviceIds),
  )
}

function selectTemplateTask(tasks: DashboardGuardTask[]) {
  return tasks.find((task) => !isDashboardGuardTask(task) && normalizeNumberIds(task.model_ids).length > 0) || null
}

function findConflictingTasks(tasks: DashboardGuardTask[], deviceIds: string[], excludeTaskId?: number) {
  const selected = new Set(deviceIds)
  return tasks.filter((task) => {
    if (task.id === excludeTaskId) return false
    if (isDashboardGuardTask(task)) return false
    if (!isTaskEnabled(task)) return false
    return normalizeStringIds(task.device_ids).some((id) => selected.has(id))
  })
}

function taskCoversAllDevices(task: DashboardGuardTask, deviceIds: string[]) {
  const taskDeviceIds = new Set(normalizeStringIds(task.device_ids))
  return deviceIds.every((id) => taskDeviceIds.has(id))
}

function findRunningAlertTaskCoveringScope(tasks: DashboardGuardTask[], deviceIds: string[]) {
  return tasks.find(
    (task) =>
      isTaskEnabled(task) &&
      isAlertEventEnabled(task) &&
      taskCoversAllDevices(task, deviceIds),
  )
}

function formatConflictMessage(conflicts: DashboardGuardTask[]) {
  const names = conflicts.map((task) => task.task_name || `Task ${task.id}`).join(', ')
  return `Selected devices are already used by running algorithm tasks: ${names}`
}

async function buildBootstrapTemplate(api: DashboardGuardTaskApi): Promise<DashboardGuardTask> {
  const modelIds = normalizeAvailableModelIds(await api.listAvailableModels())
  if (!modelIds.length) {
    throw new Error('No available AI models were found for dashboard recognition.')
  }
  return {
    id: 0,
    task_type: 'realtime',
    model_ids: modelIds,
    extract_interval: 12,
    tracking_enabled: false,
    alert_event_enabled: true,
    alert_event_suppress_time: 5,
    alert_class_names: [],
    face_detection_enabled: true,
    plate_detection_enabled: true,
    alarm_suppress_time: 300,
    defense_mode: 'full',
    schedule_policy: 'local',
  }
}

export function buildDashboardGuardTaskPayload(
  scope: DashboardGuardScope,
  template: DashboardGuardTask,
): DashboardGuardTaskPayload {
  const modelIds = normalizeNumberIds(template.model_ids)
  if (!modelIds.length) {
    throw new Error('No realtime algorithm task with models is available for dashboard guard recognition.')
  }

  return {
    task_name: buildDashboardGuardTaskName(scope),
    task_type: 'realtime',
    device_ids: uniqueStrings(scope.deviceIds),
    model_ids: modelIds,
    extract_interval: template.extract_interval,
    tracking_enabled: template.tracking_enabled,
    tracking_similarity_threshold: template.tracking_similarity_threshold,
    tracking_max_age: template.tracking_max_age,
    tracking_smooth_alpha: template.tracking_smooth_alpha,
    alert_event_enabled: true,
    alert_event_suppress_time: template.alert_event_suppress_time,
    alert_class_names: normalizeAlertClassNames(template.alert_class_names),
    face_detection_enabled: true,
    plate_detection_enabled: true,
    alert_notification_enabled: template.alert_notification_enabled,
    alert_notification_config: template.alert_notification_config,
    alarm_suppress_time: template.alarm_suppress_time,
    is_enabled: false,
    defense_mode: template.defense_mode,
    defense_schedule: template.defense_schedule,
    schedule_policy: template.schedule_policy,
    target_node_id: template.target_node_id,
  }
}

function unwrapTaskId(response: unknown): number | null {
  if (response && typeof response === 'object') {
    const record = response as { id?: unknown; data?: unknown }
    if (Number.isFinite(Number(record.id))) return Number(record.id)
    if (record.data && typeof record.data === 'object') {
      const nestedId = (record.data as { id?: unknown }).id
      if (Number.isFinite(Number(nestedId))) return Number(nestedId)
    }
  }
  return null
}

async function stopOtherDashboardGuardTasks(
  api: DashboardGuardTaskApi,
  tasks: DashboardGuardTask[],
  scope: DashboardGuardScope,
  preservedTaskId?: number,
) {
  const otherEnabledTasks = tasks.filter(
    (task) =>
      task.id !== preservedTaskId &&
      isDashboardGuardTask(task) &&
      !isDashboardGuardTaskForScope(task, scope) &&
      isTaskEnabled(task),
  )
  await Promise.all(otherEnabledTasks.map((task) => api.stopAlgorithmTask(task.id)))
}

async function startDashboardGuardTaskOnce(options: StartDashboardGuardTaskOptions) {
  const { scope, api } = options
  const deviceIds = uniqueStrings(scope.deviceIds)
  if (!deviceIds.length) {
    throw new Error('No synced devices were found under the selected dashboard guard scope.')
  }

  const tasks = await listRealtimeTasks(api)
  const reusableTask = findReusableDashboardTask(tasks, { ...scope, deviceIds })
  const runningCoveringTask = findRunningAlertTaskCoveringScope(tasks, deviceIds)
  const templateTask = selectTemplateTask(tasks) || reusableTask || runningCoveringTask ||
    await buildBootstrapTemplate(api)

  const conflicts = runningCoveringTask
    ? []
    : findConflictingTasks(tasks, deviceIds, reusableTask?.id)
  if (conflicts.length) {
    throw new Error(formatConflictMessage(conflicts))
  }

  await stopOtherDashboardGuardTasks(api, tasks, scope, runningCoveringTask?.id)

  if (runningCoveringTask) {
    return { taskId: runningCoveringTask.id, reusedExistingTask: true }
  }

  if (reusableTask) {
    if (!isTaskEnabled(reusableTask)) {
      await api.startAlgorithmTask(reusableTask.id)
    }
    return { taskId: reusableTask.id }
  }

  const created = await api.createAlgorithmTask(
    buildDashboardGuardTaskPayload({ ...scope, deviceIds }, templateTask),
  )
  const taskId = unwrapTaskId(created)
  if (!taskId) {
    throw new Error('Dashboard guard task was created but no task id was returned.')
  }
  await api.startAlgorithmTask(taskId)
  return { taskId }
}

const startRequestsByScope = new Map<
  string,
  Promise<{ taskId: number; reusedExistingTask?: boolean }>
>()

export function startDashboardGuardTask(options: StartDashboardGuardTaskOptions) {
  const deviceIds = uniqueStrings(options.scope.deviceIds)
  const scope = { ...options.scope, deviceIds }
  const scopeKey = `${scope.key}:${[...deviceIds].sort().join(',')}`
  const existingRequest = startRequestsByScope.get(scopeKey)
  if (existingRequest) return existingRequest

  const request = startDashboardGuardTaskOnce({ ...options, scope }).finally(() => {
    if (startRequestsByScope.get(scopeKey) === request) {
      startRequestsByScope.delete(scopeKey)
    }
  })
  startRequestsByScope.set(scopeKey, request)
  return request
}

export async function stopDashboardGuardTask(options: StartDashboardGuardTaskOptions) {
  const { scope, api } = options
  const tasks = await listRealtimeTasks(api)
  const ownedTasks = tasks.filter((task) => isDashboardGuardTaskForScope(task, scope) && isTaskEnabled(task))
  await Promise.all(ownedTasks.map((task) => api.stopAlgorithmTask(task.id)))
  return { stoppedTaskIds: ownedTasks.map((task) => task.id) }
}

export async function getDashboardGuardStateForScope(options: GuardStateOptions) {
  const { scope, api } = options
  if (!scope) return { enabled: false, taskId: null as number | null }
  const tasks = await listRealtimeTasks(api)
  const deviceIds = uniqueStrings(scope.deviceIds)
  const task =
    tasks.find((item) => isDashboardGuardTaskForScope(item, scope) && isTaskEnabled(item)) ||
    findRunningAlertTaskCoveringScope(tasks, deviceIds)
  return { enabled: !!task, taskId: task?.id ?? null }
}

export function createDashboardGuardScopeFromNode(node: DashboardGuardNode): DashboardGuardScope {
  const key = String(node.key ?? '')
  const label = String(node.title ?? key)
  const deviceIds: string[] = []
  const seen = new Set<string>()

  const pushDeviceId = (id: unknown) => {
    const value = String(id ?? '').trim()
    if (!value || seen.has(value)) return
    seen.add(value)
    deviceIds.push(value)
  }

  const walk = (current: DashboardGuardNode) => {
    const currentKey = String(current.key ?? '')
    if (current.device?.id) {
      pushDeviceId(current.device.id)
    } else if (currentKey.startsWith('device_')) {
      pushDeviceId(currentKey.slice('device_'.length))
    }
    current.children?.forEach(walk)
  }

  walk(node)
  return { key, label, deviceIds }
}

export function extractDashboardGuardErrorMessage(error: unknown) {
  if (error && typeof error === 'object') {
    const candidate =
      (error as { response?: { data?: { msg?: string; message?: string } } }).response?.data?.msg ||
      (error as { response?: { data?: { msg?: string; message?: string } } }).response?.data?.message ||
      (error as { data?: { msg?: string; message?: string } }).data?.msg ||
      (error as { data?: { msg?: string; message?: string } }).data?.message ||
      (error as { msg?: string }).msg ||
      (error as { message?: string }).message
    if (candidate) return candidate
  }
  return 'Dashboard guard recognition failed.'
}
