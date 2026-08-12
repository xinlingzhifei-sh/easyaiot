import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const readSource = (relativePath: string) =>
  readFileSync(fileURLToPath(new URL(`../${relativePath}`, import.meta.url)), 'utf8')

const calculateApi = readSource('src/api/device/calculate.ts')
const modelApi = readSource('src/api/device/model.ts')
const modelListApi = modelApi.slice(0, modelApi.indexOf('export const createModel'))
const dashboardData = readSource('src/views/dashboard/monitor/useDashboardData.ts')
const indexView = readSource('src/views/dashboard/monitor/index.vue')
const guardTask = readSource('src/views/dashboard/monitor/dashboardGuardTask.ts')
const videoMonitor = readSource('src/views/dashboard/monitor/components/VideoMonitor.vue')
const videoTemplate = videoMonitor.slice(0, videoMonitor.indexOf('</template>'))
const header = readSource('src/views/dashboard/monitor/components/Header.vue')
const sidebar = readSource('src/views/dashboard/monitor/components/Sidebar.vue')
const alarmPanel = readSource('src/views/dashboard/monitor/components/AlarmPanel.vue')

assert.match(
  calculateApi,
  /retryRequest:\s*\{\s*isOpenRetry:\s*false,\s*count:\s*0,\s*waitTime:\s*0\s*\}/,
  'Dashboard polling must disable the global retry loop.',
)
assert.doesNotMatch(
  calculateApi,
  /localStorage\.getItem\(['"]jwt_token['"]\)/,
  'Dashboard API calls must rely on the shared Authorization interceptor.',
)
assert.doesNotMatch(
  modelListApi,
  /localStorage\.getItem\(['"]jwt_token['"]\)/,
  'AI model loading must rely on the shared Authorization interceptor.',
)
assert.match(
  calculateApi,
  /getDashboardStatistics\s*=\s*async\s*\(params:\s*\{\s*device_id:\s*string\s*\}\)/,
  'Dashboard statistics must require a camera scope.',
)
assert.match(
  dashboardData,
  /useDashboardData\(activeDeviceId:\s*ComputedRef<string>\)/,
  'The shared dashboard data source must receive the active camera ID.',
)
assert.match(
  dashboardData,
  /getDashboardStatistics\(\{\s*device_id:\s*deviceId\s*\}\)/,
  'Statistics polling must carry the active camera ID.',
)
assert.match(
  dashboardData,
  /queryAlarmList\(\{[\s\S]*?device_id:\s*deviceId[\s\S]*?\},\s*\{\s*polling:\s*true\s*\}\)/,
  'Alert polling must carry the active camera ID and disable retries.',
)
assert.match(
  indexView,
  /const activeDeviceId = computed\([\s\S]*?device\?\.id[\s\S]*?gb_ch_/,
  'The command center must derive a real backend device ID and reject GB UI-only IDs.',
)
assert.match(
  indexView,
  /useDashboardData\(activeDeviceId\)/,
  'The command center must pass its selected device scope to the shared data source.',
)

assert.match(
  guardTask,
  /listAvailableModels/,
  'Dashboard AI startup must be able to load models when no task template exists.',
)
assert.match(
  guardTask,
  /buildBootstrapTemplate/,
  'Dashboard AI startup must build an in-memory task template from available models.',
)
assert.match(
  guardTask,
  /alert_class_names:\s*\[\]/,
  'The bootstrap task must use the documented empty alert-class contract.',
)
assert.match(
  guardTask,
  /startRequestsByScope/,
  'Concurrent AI starts for one scope must share the same request.',
)
assert.match(
  videoMonitor,
  /getModelPage\(\s*\{\s*pageNo:\s*1,\s*pageSize:\s*1000\s*\},\s*\{\s*errorMessageMode:\s*['"]none['"]\s*\},?\s*\)/,
  'The command center must supply a locally handled available-model loader to dashboard AI startup.',
)

assert.match(indexView, /class="monitor-content"/, 'The command center must keep the video-first grid.')
assert.match(indexView, /useDashboardData\(activeDeviceId\)/, 'Dashboard reads must use the selected camera scope.')
assert.match(header, /data-testid="monitor-admin-entry"/, 'The admin entry must remain visible in the header.')
assert.match(header, /const adminEntryLabel = ['"]管理后台['"]/, 'The admin entry label must remain 管理后台.')
assert.doesNotMatch(sidebar, /statistics-cards/, 'Duplicate overview cards must be removed from the device rail.')
assert.match(videoTemplate, /data-testid="monitor-split-toolbar"/, 'The newer multi-view surface must retain split controls.')
assert.match(videoTemplate, /LayoutPresetPanel/, 'The newer multi-view surface must retain layout presets.')
assert.match(videoTemplate, /data-testid="monitor-ai-toggle"/, 'The video header must retain the AI switch.')
assert.match(alarmPanel, /dashboardHealth/, 'The alert rail must receive scoped interface health.')
assert.match(alarmPanel, /refreshDashboardData|emit\(['"]retry['"]\)/, 'The alert rail must expose a manual retry action.')
